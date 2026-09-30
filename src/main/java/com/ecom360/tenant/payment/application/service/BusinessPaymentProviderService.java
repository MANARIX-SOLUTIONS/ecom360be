package com.ecom360.tenant.payment.application.service;

import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.payment.application.dto.BictorysConnectionTestResponse;
import com.ecom360.tenant.payment.application.dto.BictorysSettingsRequest;
import com.ecom360.tenant.payment.application.dto.BictorysSettingsResponse;
import com.ecom360.tenant.payment.domain.model.BusinessPaymentProvider;
import com.ecom360.tenant.payment.domain.repository.BusinessPaymentProviderRepository;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysClient;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysCredentials;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-business Bictorys merchant account used by the POS (plan Business). */
@Service
public class BusinessPaymentProviderService {

  public static final String POS_WEBHOOK_PATH =
      ApiConstants.API_BASE + "/public/payments/bictorys/pos/";

  public static final String NOT_CONFIGURED =
      "Configurez votre compte Bictorys dans Paramètres › Paiements Bictorys.";

  private final BusinessPaymentProviderRepository repository;
  private final SubscriptionService subscriptionService;
  private final BictorysClient bictorysClient;
  private final AuditLogService auditLogService;
  private final SecureRandom random = new SecureRandom();

  public BusinessPaymentProviderService(
      BusinessPaymentProviderRepository repository,
      SubscriptionService subscriptionService,
      BictorysClient bictorysClient,
      AuditLogService auditLogService) {
    this.repository = repository;
    this.subscriptionService = subscriptionService;
    this.bictorysClient = bictorysClient;
    this.auditLogService = auditLogService;
  }

  @Transactional(readOnly = true)
  public BictorysSettingsResponse getBictorys(UserPrincipal p) {
    requireOwner(p);
    subscriptionService.requirePosOnlinePayment(p.businessId());
    return toResponse(find(p.businessId()).orElse(null));
  }

  @Transactional
  public BictorysSettingsResponse saveBictorys(BictorysSettingsRequest req, UserPrincipal p) {
    requireOwner(p);
    subscriptionService.requirePosOnlinePayment(p.businessId());

    BusinessPaymentProvider cfg = find(p.businessId()).orElse(null);
    boolean creating = cfg == null;
    if (creating) {
      cfg = new BusinessPaymentProvider();
      cfg.setBusinessId(p.businessId());
      cfg.setProvider(BusinessPaymentProvider.BICTORYS);
      cfg.setWebhookToken(newWebhookToken());
    }
    String apiKey = trimToNull(req.apiKey());
    String secret = trimToNull(req.webhookSecret());
    if (apiKey != null) {
      cfg.setApiKey(apiKey);
    }
    if (secret != null) {
      cfg.setWebhookSecret(secret);
    }
    if (cfg.getApiKey() == null || cfg.getWebhookSecret() == null) {
      throw new BusinessRuleException("La clé API et le secret webhook Bictorys sont requis.");
    }
    cfg.setEnvironment(req.environment());
    if (req.country() != null) {
      cfg.setCountry(req.country());
    }
    if (req.enabled() != null) {
      cfg.setEnabled(req.enabled());
    }
    cfg = repository.save(cfg);

    auditLogService.log(
        p.businessId(),
        p.userId(),
        creating ? "payment_provider.bictorys.created" : "payment_provider.bictorys.updated",
        "BusinessPaymentProvider",
        cfg.getId(),
        Map.of(
            "environment", cfg.getEnvironment(),
            "enabled", cfg.getEnabled(),
            "apiKeyChanged", apiKey != null,
            "webhookSecretChanged", secret != null),
        null);
    return toResponse(cfg);
  }

  @Transactional(readOnly = true)
  public BictorysConnectionTestResponse testBictorys(UserPrincipal p) {
    requireOwner(p);
    subscriptionService.requirePosOnlinePayment(p.businessId());
    BusinessPaymentProvider cfg = find(p.businessId())
        .orElseThrow(() -> new BusinessRuleException(NOT_CONFIGURED));
    boolean ok = bictorysClient.isApiKeyAccepted(credentials(cfg));
    return new BictorysConnectionTestResponse(
        ok,
        ok
            ? "Connexion Bictorys réussie."
            : "Clé API refusée par Bictorys. Vérifiez la clé et l'environnement (test / live).");
  }

  /** Usable credentials for charges; fails with an actionable message otherwise. */
  @Transactional(readOnly = true)
  public BictorysCredentials requireUsableCredentials(UUID businessId) {
    BusinessPaymentProvider cfg = find(businessId)
        .filter(BusinessPaymentProvider::isUsable)
        .orElseThrow(() -> new BusinessRuleException(NOT_CONFIGURED));
    return credentials(cfg);
  }

  @Transactional(readOnly = true)
  public boolean isConfigured(UUID businessId) {
    return find(businessId).map(BusinessPaymentProvider::isUsable).orElse(false);
  }

  /**
   * Status sync must keep working after a downgrade or when the account is
   * disabled, so pending intents still settle; only the key is required.
   */
  @Transactional(readOnly = true)
  public Optional<BictorysCredentials> findCredentials(UUID businessId) {
    return find(businessId).map(this::credentials);
  }

  @Transactional(readOnly = true)
  public Optional<BusinessPaymentProvider> findByWebhookToken(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    return repository.findByWebhookToken(token.trim());
  }

  public BictorysCredentials credentials(BusinessPaymentProvider cfg) {
    return BictorysCredentials.forEnvironment(
        cfg.isLive(), cfg.getApiKey(), cfg.getWebhookSecret(), cfg.getCountry());
  }

  private Optional<BusinessPaymentProvider> find(UUID businessId) {
    return repository.findByBusinessIdAndProvider(businessId, BusinessPaymentProvider.BICTORYS);
  }

  private BictorysSettingsResponse toResponse(BusinessPaymentProvider cfg) {
    if (cfg == null) {
      return new BictorysSettingsResponse(false, false, "test", "SN", null, null, null, null);
    }
    return new BictorysSettingsResponse(
        cfg.isUsable(),
        Boolean.TRUE.equals(cfg.getEnabled()),
        cfg.getEnvironment(),
        cfg.getCountry(),
        mask(cfg.getApiKey()),
        mask(cfg.getWebhookSecret()),
        POS_WEBHOOK_PATH + cfg.getWebhookToken(),
        cfg.getUpdatedAt());
  }

  static String mask(String secret) {
    if (secret == null || secret.isBlank()) {
      return null;
    }
    int n = secret.length();
    if (n <= 8) {
      return "••••";
    }
    return secret.substring(0, 4) + "••••" + secret.substring(n - 4);
  }

  private String newWebhookToken() {
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static void requireOwner(UserPrincipal p) {
    if (!p.hasBusinessAccess() || p.businessId() == null) {
      throw new AccessDeniedException("Business context required");
    }
    String role = p.role() != null ? p.role() : "";
    if (!"proprietaire".equalsIgnoreCase(role) && !p.isPlatformAdmin()) {
      throw new AccessDeniedException(
          "Seul le rôle propriétaire peut gérer le compte de paiement Bictorys");
    }
  }

  private static String trimToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
