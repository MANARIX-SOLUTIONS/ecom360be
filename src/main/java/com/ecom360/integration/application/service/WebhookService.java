package com.ecom360.integration.application.service;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.integration.application.dto.WebhookCreateResponse;
import com.ecom360.integration.application.dto.WebhookRequest;
import com.ecom360.integration.application.dto.WebhookResponse;
import com.ecom360.integration.application.dto.WebhookTestResponse;
import com.ecom360.integration.domain.model.Webhook;
import com.ecom360.integration.domain.repository.WebhookRepository;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

  private static final int CONNECT_TIMEOUT_MS = 5_000;
  private static final int READ_TIMEOUT_MS = 10_000;

  private final WebhookRepository webhookRepository;
  private final RolePermissionService permissionService;
  private final SubscriptionService subscriptionService;
  private final ObjectMapper objectMapper;

  public WebhookService(
      WebhookRepository webhookRepository,
      RolePermissionService permissionService,
      SubscriptionService subscriptionService,
      ObjectMapper objectMapper) {
    this.webhookRepository = webhookRepository;
    this.permissionService = permissionService;
    this.subscriptionService = subscriptionService;
    this.objectMapper = objectMapper;
  }

  private void requireApi(UserPrincipal p) {
    subscriptionService.requireFeatureApi(p.businessId());
  }

  @Transactional
  public WebhookCreateResponse create(WebhookRequest request, UserPrincipal p) {
    requireBiz(p);
    requireApi(p);
    permissionService.require(p, Permission.WEBHOOKS_CREATE);
    validateOutboundUrl(request.url());
    String secret = generateSecret();
    String secretHash = hashSecret(secret);

    Webhook webhook = new Webhook();
    webhook.setBusinessId(p.businessId());
    webhook.setUrl(request.url());
    webhook.setEvents(request.events());
    webhook.setSecretHash(secretHash);
    webhook.setIsActive(request.isActive());
    webhook = webhookRepository.save(webhook);

    return new WebhookCreateResponse(
        webhook.getId(),
        webhook.getBusinessId(),
        webhook.getUrl(),
        webhook.getEvents(),
        webhook.getIsActive(),
        webhook.getCreatedAt(),
        webhook.getUpdatedAt(),
        secret);
  }

  public List<WebhookResponse> list(UserPrincipal p) {
    requireBiz(p);
    requireApi(p);
    permissionService.require(p, Permission.WEBHOOKS_READ);
    return webhookRepository.findByBusinessId(p.businessId()).stream()
        .map(this::toResponse)
        .toList();
  }

  public WebhookResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    requireApi(p);
    permissionService.require(p, Permission.WEBHOOKS_READ);
    return toResponse(find(id, p));
  }

  @Transactional
  public WebhookResponse update(UUID id, WebhookRequest request, UserPrincipal p) {
    requireBiz(p);
    requireApi(p);
    permissionService.require(p, Permission.WEBHOOKS_UPDATE);
    validateOutboundUrl(request.url());
    Webhook webhook = find(id, p);
    webhook.setUrl(request.url());
    webhook.setEvents(request.events());
    webhook.setIsActive(request.isActive());
    webhook = webhookRepository.save(webhook);
    return toResponse(webhook);
  }

  @Transactional
  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
    requireApi(p);
    permissionService.require(p, Permission.WEBHOOKS_DELETE);
    webhookRepository.delete(find(id, p));
  }

  /**
   * Envoie un événement {@code webhook.test} vers l'URL configurée (sans
   * signature —
   * le secret n'est pas stocké en clair). Redirections désactivées ; IP épinglée.
   */
  public WebhookTestResponse sendTest(UUID id, UserPrincipal p) {
    requireBiz(p);
    requireApi(p);
    permissionService.require(p, Permission.WEBHOOKS_UPDATE);
    Webhook webhook = find(id, p);
    if (Boolean.FALSE.equals(webhook.getIsActive())) {
      throw new BusinessRuleException("Webhook inactif — activez-le avant le test.");
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("event", "webhook.test");
    body.put("webhookId", webhook.getId().toString());
    body.put("businessId", p.businessId().toString());
    body.put("sentAt", Instant.now().toString());

    long started = System.currentTimeMillis();
    try {
      URI uri = validateOutboundUrl(webhook.getUrl());
      byte[] payload = objectMapper.writeValueAsBytes(body);
      int status = postNoRedirect(uri, payload);
      long durationMs = System.currentTimeMillis() - started;
      boolean success = status >= 200 && status < 300;
      return new WebhookTestResponse(
          success,
          status,
          success ? "Événement de test livré" : "Réponse HTTP non réussie",
          durationMs);
    } catch (BusinessRuleException ex) {
      throw ex;
    } catch (Exception ex) {
      long durationMs = System.currentTimeMillis() - started;
      return new WebhookTestResponse(false, 0, "Échec d'envoi", durationMs);
    }
  }

  /**
   * Valide l'URL (create/update/test) : schéma http(s), pas d'userinfo, toutes
   * les IP
   * résolues publiques. Retourne l'URI normalisée.
   */
  static URI validateOutboundUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url.trim());
    } catch (IllegalArgumentException e) {
      throw new BusinessRuleException("URL webhook invalide");
    }
    String scheme = uri.getScheme();
    if (scheme == null
        || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
      throw new BusinessRuleException("L'URL webhook doit commencer par http:// ou https://");
    }
    if (uri.getUserInfo() != null) {
      throw new BusinessRuleException("URL webhook avec identifiants interdite");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new BusinessRuleException("URL webhook sans hôte");
    }
    String lower = host.toLowerCase();
    if ("localhost".equals(lower)
        || lower.endsWith(".localhost")
        || lower.endsWith(".local")
        || lower.endsWith(".internal")
        || "metadata.google.internal".equals(lower)) {
      throw new BusinessRuleException(
          "Les adresses locales / privées ne sont pas autorisées pour les webhooks");
    }

    assertPublicDns(host);
    // Seconde résolution : réduit la fenêtre de DNS rebinding.
    assertPublicDns(host);
    return uri;
  }

  private static void assertPublicDns(String host) {
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (Exception e) {
      throw new BusinessRuleException("Impossible de résoudre l'hôte du webhook");
    }
    if (addresses.length == 0) {
      throw new BusinessRuleException("Impossible de résoudre l'hôte du webhook");
    }
    for (InetAddress addr : addresses) {
      if (isBlockedAddress(addr)) {
        throw new BusinessRuleException(
            "Les adresses locales / privées ne sont pas autorisées pour les webhooks");
      }
    }
  }

  private int postNoRedirect(URI uri, byte[] payload) throws IOException {
    URL url = uri.toURL();
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setInstanceFollowRedirects(false);
    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
    conn.setReadTimeout(READ_TIMEOUT_MS);
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    conn.setRequestProperty("User-Agent", "Ecom360-Webhook-Test/1.0");
    conn.setRequestProperty("X-Ecom360-Event", "webhook.test");
    conn.setFixedLengthStreamingMode(payload.length);

    try (OutputStream os = conn.getOutputStream()) {
      os.write(payload);
    }
    return conn.getResponseCode();
  }

  static boolean isBlockedAddress(InetAddress addr) {
    if (addr.isAnyLocalAddress()
        || addr.isLoopbackAddress()
        || addr.isLinkLocalAddress()
        || addr.isSiteLocalAddress()
        || addr.isMulticastAddress()) {
      return true;
    }
    byte[] b = addr.getAddress();
    if (b.length == 4) {
      int a0 = b[0] & 0xff;
      int a1 = b[1] & 0xff;
      // 100.64.0.0/10 (CGNAT)
      if (a0 == 100 && a1 >= 64 && a1 <= 127)
        return true;
      // 169.254.0.0/16 (link-local / cloud metadata)
      if (a0 == 169 && a1 == 254)
        return true;
      // 0.0.0.0/8
      if (a0 == 0)
        return true;
    }
    if (b.length == 16) {
      // fc00::/7 unique local
      if ((b[0] & 0xfe) == 0xfc)
        return true;
      // fe80::/10 link-local already covered by isLinkLocalAddress
    }
    return false;
  }

  private Webhook find(UUID id, UserPrincipal p) {
    return webhookRepository
        .findByBusinessIdAndId(p.businessId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("Webhook", id));
  }

  private WebhookResponse toResponse(Webhook w) {
    return new WebhookResponse(
        w.getId(),
        w.getBusinessId(),
        w.getUrl(),
        w.getEvents(),
        w.getIsActive(),
        w.getCreatedAt(),
        w.getUpdatedAt());
  }

  private String generateSecret() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hashSecret(String secret) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess())
      throw new AccessDeniedException("Business context required");
  }
}
