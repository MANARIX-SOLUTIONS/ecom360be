package com.ecom360.sales.application.service;

import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.client.domain.model.Client;
import com.ecom360.client.domain.repository.ClientRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.sales.application.dto.DigitalCheckoutAvailabilityResponse;
import com.ecom360.sales.application.dto.DigitalCheckoutRequest;
import com.ecom360.sales.application.dto.DigitalCheckoutResponse;
import com.ecom360.sales.domain.model.Sale;
import com.ecom360.sales.domain.model.SalePaymentIntent;
import com.ecom360.sales.domain.model.SalePaymentIntentStatus;
import com.ecom360.sales.domain.repository.SalePaymentIntentRepository;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.payment.application.service.BusinessPaymentProviderService;
import com.ecom360.tenant.payment.domain.PaymentIntentNotFoundException;
import com.ecom360.tenant.payment.domain.model.BusinessPaymentProvider;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysChargeResult;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysClient;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysCredentials;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysProperties;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysStatusResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * POS Wave / Orange Money checkout on the business's own Bictorys account (plan
 * Business). The sale is created {@code pending_payment} with stock reserved; the
 * webhook, status polling or the expiration job then completes or releases it.
 */
@Service
public class PosDigitalCheckoutService {

  private static final Logger log = LoggerFactory.getLogger(PosDigitalCheckoutService.class);

  public static final Duration PENDING_TTL = Duration.ofMinutes(15);
  static final String PROVIDER = "bictorys";
  private static final int CHECKOUT_URL_MAX = 1000;

  private final SalePaymentIntentRepository intentRepository;
  private final SaleRepository saleRepository;
  private final SaleService saleService;
  private final ClientRepository clientRepository;
  private final BusinessPaymentProviderService providerService;
  private final BictorysClient bictorysClient;
  private final BictorysProperties bictorysProperties;
  private final SubscriptionService subscriptionService;
  private final RolePermissionService permissionService;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;
  private final EntityManager entityManager;
  private final TransactionTemplate txTemplate;
  private final String appUrl;

  public PosDigitalCheckoutService(
      SalePaymentIntentRepository intentRepository,
      SaleRepository saleRepository,
      SaleService saleService,
      ClientRepository clientRepository,
      BusinessPaymentProviderService providerService,
      BictorysClient bictorysClient,
      BictorysProperties bictorysProperties,
      SubscriptionService subscriptionService,
      RolePermissionService permissionService,
      AuditLogService auditLogService,
      ObjectMapper objectMapper,
      EntityManager entityManager,
      PlatformTransactionManager transactionManager,
      @Value("${app.url:http://localhost:5173}") String appUrl) {
    this.intentRepository = intentRepository;
    this.saleRepository = saleRepository;
    this.saleService = saleService;
    this.clientRepository = clientRepository;
    this.providerService = providerService;
    this.bictorysClient = bictorysClient;
    this.bictorysProperties = bictorysProperties;
    this.subscriptionService = subscriptionService;
    this.permissionService = permissionService;
    this.auditLogService = auditLogService;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
    this.txTemplate = new TransactionTemplate(transactionManager);
    this.appUrl = appUrl;
  }

  /**
   * Any failure (including Bictorys refusing the charge) rolls back the sale and
   * its stock movements, so nothing is left pending.
   */
  @Transactional
  public DigitalCheckoutResponse start(DigitalCheckoutRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_CREATE);
    subscriptionService.requirePosOnlinePayment(p.businessId());
    BictorysCredentials credentials = providerService.requireUsableCredentials(p.businessId());

    Sale sale = saleService.createPendingPaymentSale(
        req.storeId(),
        req.clientId(),
        req.channel(),
        req.discountAmount(),
        req.note(),
        req.lines(),
        p);
    if (sale.getTotal() == null || sale.getTotal() <= 0) {
      throw new BusinessRuleException("Le montant à payer doit être supérieur à zéro.");
    }

    SalePaymentIntent intent = new SalePaymentIntent();
    intent.setBusinessId(p.businessId());
    intent.setStoreId(sale.getStoreId());
    intent.setSaleId(sale.getId());
    intent.setUserId(p.userId());
    intent.setAmount(sale.getTotal());
    intent.setCurrency("XOF");
    intent.setProvider(PROVIDER);
    intent.setChannel(req.channel());
    intent.setExpiresAt(Instant.now().plus(PENDING_TTL));
    intent = intentRepository.save(intent);

    String returnUrl = returnBase() + "/pos?checkout=" + intent.getId();
    Client client = clientRepository
        .findByBusinessIdAndId(p.businessId(), sale.getClientId())
        .orElse(null);

    BictorysChargeResult charge = bictorysClient.createCharge(
        credentials,
        sale.getTotal(),
        req.channel(),
        intent.getId().toString(),
        returnUrl,
        returnUrl + "&cancelled=1",
        client != null ? client.getName() : null,
        client != null ? client.getEmail() : null,
        client != null ? client.getPhone() : null);

    intent.setExternalToken(charge.transactionId());
    intent.setCheckoutUrl(fitCheckoutUrl(charge.link(), charge.redirectUrl()));
    intent.setMetadata(buildChargeMetadata(charge));
    intent = intentRepository.save(intent);

    auditLogService.log(
        p.businessId(),
        p.userId(),
        "sale.digital_checkout.created",
        "SalePaymentIntent",
        intent.getId(),
        Map.of(
            "saleId", sale.getId().toString(),
            "channel", req.channel(),
            "amount", sale.getTotal()),
        null);
    log.info(
        "POS digital checkout created intent={} sale={} channel={} amount={}",
        intent.getId(),
        sale.getId(),
        req.channel(),
        sale.getTotal());
    return toResponse(intent);
  }

  @Transactional(readOnly = true)
  public DigitalCheckoutAvailabilityResponse availability(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_CREATE);
    boolean planAllowed = subscriptionService.hasPosOnlinePayment(p.businessId());
    boolean configured = planAllowed && providerService.isConfigured(p.businessId());
    return new DigitalCheckoutAvailabilityResponse(planAllowed, configured, configured);
  }

  /** Not plan-gated: pending payments must still settle after a downgrade. */
  @Transactional
  public DigitalCheckoutResponse getStatus(UUID intentId, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_READ);
    requireOwnership(intentId, p.businessId());
    SalePaymentIntent locked = lock(intentId);
    syncLocked(locked, p.userId());
    return toResponse(locked);
  }

  /** Checks Bictorys first: a customer who just paid must not see the sale cancelled. */
  @Transactional
  public DigitalCheckoutResponse cancel(UUID intentId, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_CREATE);
    subscriptionService.requirePosOnlinePayment(p.businessId());
    requireOwnership(intentId, p.businessId());
    SalePaymentIntent locked = lock(intentId);
    syncLocked(locked, p.userId());
    if (locked.isPending()) {
      failLocked(
          locked, SalePaymentIntentStatus.CANCELLED, "Annulé depuis le POS", p.userId());
    }
    return toResponse(locked);
  }

  /** Fallback when Bictorys is unreachable but the cashier saw the payment. */
  @Transactional
  public DigitalCheckoutResponse confirmManual(UUID intentId, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_UPDATE);
    subscriptionService.requirePosOnlinePayment(p.businessId());
    requireOwnership(intentId, p.businessId());
    SalePaymentIntent locked = lock(intentId);
    if (locked.isSettled()) {
      return toResponse(locked);
    }
    if (!locked.isPending()) {
      throw new BusinessRuleException(
          "Ce paiement est clôturé (" + locked.getStatus() + ") : relancez la vente.");
    }
    locked.markManual("Confirmé manuellement depuis le POS");
    saleService.completePendingPaymentSale(
        locked.getBusinessId(),
        locked.getSaleId(),
        p.userId(),
        "Paiement " + channelLabel(locked.getChannel()) + " confirmé manuellement");
    intentRepository.save(locked);
    audit(locked, p.userId(), "sale.digital_checkout.manual", Map.of());
    return toResponse(locked);
  }

  /**
   * Handles a webhook received on the business-specific URL. The caller has
   * verified the signature with that business's secret.
   */
  @Transactional
  public void handleWebhook(BusinessPaymentProvider account, JsonNode payload) {
    if (payload == null || !payload.isObject()) {
      throw new BusinessRuleException("Webhook Bictorys invalide");
    }
    String type = text(payload, "type");
    if (type != null && !"payment".equalsIgnoreCase(type)) {
      log.info("POS Bictorys webhook ignored (type={})", type);
      return;
    }
    String status = text(payload, "status");
    String transactionId = text(payload, "id");
    String reference = text(payload, "paymentReference");
    if (reference == null || reference.isBlank()) {
      reference = text(payload, "merchantReference");
    }

    SalePaymentIntent locked = lockByReference(parseUuid(reference), transactionId);
    if (locked == null) {
      throw new PaymentIntentNotFoundException(
          "POS Bictorys webhook: intent not found (ref=" + reference + ", id=" + transactionId + ")");
    }
    if (!locked.getBusinessId().equals(account.getBusinessId())) {
      log.warn(
          "POS Bictorys webhook for intent {} received on another business's URL — ignored",
          locked.getId());
      return;
    }
    if (locked.isSettled()) {
      return;
    }

    if (BictorysStatusResult.isSucceeded(status)) {
      String currency = text(payload, "currency");
      if (currency != null && !currency.equalsIgnoreCase(locked.getCurrency())) {
        failLocked(
            locked,
            SalePaymentIntentStatus.FAILED,
            "Devise du paiement (" + currency + ") différente de la vente",
            null);
        return;
      }
      if (transactionId != null && locked.getExternalToken() == null) {
        locked.setExternalToken(transactionId);
      }
      confirmLocked(locked, intOrNull(payload, "amount"), "bictorys_webhook");
      return;
    }
    if (BictorysStatusResult.isFailed(status)) {
      failLocked(locked, SalePaymentIntentStatus.FAILED, "Paiement " + status, null);
    }
  }

  /** Expires abandoned intents, one transaction each so a failure does not block the rest. */
  public int expireStalePendingIntents() {
    List<UUID> ids = intentRepository.findIdsByStatusAndExpiresAtBefore(
        SalePaymentIntentStatus.PENDING, Instant.now());
    int expired = 0;
    for (UUID id : ids) {
      try {
        Boolean done = txTemplate.execute(status -> expireOne(id));
        if (Boolean.TRUE.equals(done)) {
          expired++;
        }
      } catch (RuntimeException e) {
        log.error("Failed to expire POS payment intent {}: {}", id, e.getMessage());
      }
    }
    return expired;
  }

  private boolean expireOne(UUID intentId) {
    SalePaymentIntent locked = lock(intentId);
    if (!locked.isPending()) {
      return false;
    }
    syncLocked(locked, null);
    if (!locked.isPending()) {
      return false;
    }
    failLocked(
        locked,
        SalePaymentIntentStatus.EXPIRED,
        "Paiement non reçu après " + PENDING_TTL.toMinutes() + " min",
        null);
    return true;
  }

  private void syncLocked(SalePaymentIntent locked, UUID actorUserId) {
    if (!locked.isPending() || locked.getExternalToken() == null) {
      return;
    }
    Optional<BictorysCredentials> credentials =
        providerService.findCredentials(locked.getBusinessId());
    if (credentials.isEmpty()) {
      return;
    }
    try {
      BictorysStatusResult result =
          bictorysClient.getStatus(credentials.get(), locked.getExternalToken());
      if (result.isSucceeded()) {
        confirmLocked(locked, result.amount(), "bictorys_status");
      } else if (result.isFailed()) {
        failLocked(
            locked, SalePaymentIntentStatus.FAILED, "Paiement " + result.status(), actorUserId);
      }
    } catch (BusinessRuleException e) {
      log.debug("POS status poll skipped for {}: {}", locked.getId(), e.getMessage());
    }
  }

  /** Must run under the intent's pessimistic lock. */
  private void confirmLocked(SalePaymentIntent intent, Integer paidAmount, String source) {
    if (intent.isSettled()) {
      return;
    }
    if (!intent.isPending()) {
      // Stock already released and sale closed: flag for manual reconciliation.
      log.error(
          "POS payment succeeded after intent {} was {} (sale={}) — reconcile manually",
          intent.getId(),
          intent.getStatus(),
          intent.getSaleId());
      audit(intent, null, "sale.digital_checkout.late_success", Map.of("source", source));
      return;
    }
    if (paidAmount != null && !paidAmount.equals(intent.getAmount())) {
      String msg = "Montant payé (" + paidAmount + ") différent de la vente (" + intent.getAmount() + ")";
      log.error("POS payment amount mismatch intent={} {}", intent.getId(), msg);
      failLocked(intent, SalePaymentIntentStatus.FAILED, msg, null);
      return;
    }
    intent.markPaid();
    saleService.completePendingPaymentSale(
        intent.getBusinessId(), intent.getSaleId(), null, null);
    intentRepository.save(intent);
    Map<String, Object> changes = new HashMap<>();
    changes.put("source", source);
    if (paidAmount != null) {
      changes.put("paidAmount", paidAmount);
    }
    audit(intent, null, "sale.digital_checkout.paid", changes);
    log.info(
        "POS digital payment confirmed intent={} sale={} channel={} source={}",
        intent.getId(),
        intent.getSaleId(),
        intent.getChannel(),
        source);
  }

  private void failLocked(
      SalePaymentIntent intent, String status, String reason, UUID actorUserId) {
    if (!intent.isPending()) {
      return;
    }
    intent.markClosed(status, reason);
    saleService.failPendingPaymentSale(intent.getBusinessId(), intent.getSaleId(), actorUserId);
    intentRepository.save(intent);
    audit(intent, actorUserId, "sale.digital_checkout." + status, Map.of("reason", reason));
    log.info(
        "POS digital payment closed intent={} sale={} status={} reason={}",
        intent.getId(),
        intent.getSaleId(),
        status,
        reason);
  }

  private void audit(
      SalePaymentIntent intent, UUID actorUserId, String action, Map<String, Object> extra) {
    Map<String, Object> changes = new HashMap<>(extra);
    changes.put("saleId", intent.getSaleId().toString());
    changes.put("channel", intent.getChannel());
    changes.put("amount", intent.getAmount());
    auditLogService.log(
        intent.getBusinessId(),
        actorUserId,
        action,
        "SalePaymentIntent",
        intent.getId(),
        changes,
        null);
  }

  private DigitalCheckoutResponse toResponse(SalePaymentIntent intent) {
    String qrCode = null;
    String paymentLink = null;
    String ussdMessage = null;
    if (intent.isPending()) {
      JsonNode meta = readMetadata(intent);
      if (meta != null) {
        qrCode = text(meta, "qrCode");
        paymentLink = text(meta, "link");
        ussdMessage = text(meta, "message");
      }
    }
    Sale sale = saleRepository
        .findByBusinessIdAndId(intent.getBusinessId(), intent.getSaleId())
        .orElse(null);
    return new DigitalCheckoutResponse(
        intent.getId(),
        intent.getStatus(),
        intent.getChannel(),
        intent.getAmount(),
        intent.getCurrency(),
        intent.getSaleId(),
        sale != null ? sale.getReceiptNumber() : null,
        intent.getCheckoutUrl(),
        qrCode,
        paymentLink,
        ussdMessage,
        intent.getExpiresAt(),
        intent.getFailureReason(),
        intent.isSettled() && sale != null ? saleService.toResponse(sale) : null);
  }

  private SalePaymentIntent lock(UUID intentId) {
    SalePaymentIntent intent = intentRepository
        .findByIdForUpdate(intentId)
        .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", intentId));
    return fresh(intent);
  }

  private SalePaymentIntent lockByReference(UUID intentId, String token) {
    if (intentId != null) {
      SalePaymentIntent byId = intentRepository.findByIdForUpdate(intentId).orElse(null);
      if (byId != null) {
        return fresh(byId);
      }
    }
    if (token != null && !token.isBlank()) {
      return fresh(intentRepository.findByExternalTokenForUpdate(token).orElse(null));
    }
    return null;
  }

  /** Re-reads the row under the lock so the state checked is never a stale session copy. */
  private SalePaymentIntent fresh(SalePaymentIntent intent) {
    if (intent != null && entityManager.contains(intent)) {
      entityManager.refresh(intent, LockModeType.PESSIMISTIC_WRITE);
    }
    return intent;
  }

  /** Scalar lookup so the intent is not loaded before being locked. */
  private void requireOwnership(UUID intentId, UUID businessId) {
    UUID owner = intentRepository
        .findBusinessIdById(intentId)
        .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", intentId));
    if (!owner.equals(businessId)) {
      throw new AccessDeniedException("Payment intent does not belong to this business");
    }
  }

  private String returnBase() {
    String base = bictorysProperties.getRedirectBaseUrl();
    String url = base == null || base.isBlank() ? appUrl : base;
    if (url == null || url.isBlank()) {
      url = "http://localhost:5173";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  static String fitCheckoutUrl(String link, String redirectUrl) {
    for (String candidate : new String[] {link, redirectUrl}) {
      if (candidate != null && !candidate.isBlank() && candidate.length() <= CHECKOUT_URL_MAX) {
        return candidate;
      }
    }
    return null;
  }

  private String buildChargeMetadata(BictorysChargeResult charge) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("transactionId", charge.transactionId());
    node.put("redirectUrl", charge.redirectUrl());
    node.put("link", charge.link());
    node.put("qrCode", charge.qrCode());
    node.put("message", charge.message());
    try {
      return objectMapper.writeValueAsString(node);
    } catch (Exception e) {
      log.warn("Unable to serialize POS charge metadata: {}", e.getMessage());
      return null;
    }
  }

  private JsonNode readMetadata(SalePaymentIntent intent) {
    if (intent.getMetadata() == null || intent.getMetadata().isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(intent.getMetadata());
    } catch (Exception e) {
      return null;
    }
  }

  private static String channelLabel(String channel) {
    return "wave".equals(channel) ? "Wave" : "Orange Money";
  }

  private static UUID parseUuid(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode n = node.get(field);
    return n == null || n.isNull() ? null : n.asText();
  }

  private static Integer intOrNull(JsonNode node, String field) {
    JsonNode n = node.get(field);
    if (n == null || n.isNull()) {
      return null;
    }
    if (n.isNumber()) {
      return n.asInt();
    }
    try {
      return (int) Double.parseDouble(n.asText().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess() || p.businessId() == null) {
      throw new AccessDeniedException("Business context required");
    }
  }
}
