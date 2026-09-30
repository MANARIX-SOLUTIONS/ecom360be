package com.ecom360.tenant.payment.infrastructure.bictorys;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BictorysClient {

  private static final Logger log = LoggerFactory.getLogger(BictorysClient.class);

  static final int MAX_RETRIES = 3;
  static final long WEBHOOK_MAX_SKEW_MS = 5 * 60 * 1000L;

  private final BictorysProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public BictorysClient(BictorysProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(10));
    factory.setReadTimeout(Duration.ofSeconds(30));
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /** Platform account (subscriptions). */
  public BictorysChargeResult createCharge(
      int amount,
      String channel,
      String paymentReference,
      String successRedirectUrl,
      String errorRedirectUrl,
      String customerName,
      String customerEmail,
      String customerPhone) {
    requireEnabled();
    return createCharge(
        platformCredentials(),
        amount,
        channel,
        paymentReference,
        successRedirectUrl,
        errorRedirectUrl,
        customerName,
        customerEmail,
        customerPhone);
  }

  public BictorysChargeResult createCharge(
      BictorysCredentials credentials,
      int amount,
      String channel,
      String paymentReference,
      String successRedirectUrl,
      String errorRedirectUrl,
      String customerName,
      String customerEmail,
      String customerPhone) {
    requireApiKey(credentials);

    String paymentType = toPaymentType(channel);
    if (paymentType == null) {
      throw new BusinessRuleException("Canal de paiement non supporté par Bictorys");
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("amount", amount);
    body.put("currency", "XOF");
    body.put("country", credentials.country());
    body.put("paymentReference", paymentReference);
    // Bictorys' WAF answers 403 when a redirect targets localhost/private IPs.
    if (isPublicUrl(successRedirectUrl)) {
      body.put("successRedirectUrl", successRedirectUrl);
    } else if (!isBlank(successRedirectUrl)) {
      log.warn(
          "Bictorys redirect URL {} is not publicly reachable, omitting redirects "
              + "(set BICTORYS_REDIRECT_BASE_URL to a public URL)",
          successRedirectUrl);
    }
    if (isPublicUrl(errorRedirectUrl)) {
      // Bictorys expects a capital E on this field.
      body.put("ErrorRedirectUrl", errorRedirectUrl);
    }
    Map<String, String> customer = new LinkedHashMap<>();
    if (!isBlank(customerName)) {
      customer.put("name", customerName);
    }
    if (!isBlank(customerEmail)) {
      customer.put("email", customerEmail);
    }
    String phone = normalizePhone(customerPhone);
    if (phone != null) {
      customer.put("phone", phone);
    }
    if (!customer.isEmpty()) {
      customer.put("country", credentials.country());
      body.put("customerObject", customer);
    }

    String url = apiRoot(credentials) + "/pay/v1/charges?payment_type=" + paymentType;
    // Pre-serialized so the request carries Content-Length; Bictorys fails on
    // chunked bodies.
    byte[] payload;
    try {
      payload = objectMapper.writeValueAsBytes(body);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot serialize Bictorys charge", e);
    }
    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      if (attempt > 0) {
        sleep((long) Math.pow(2, attempt) * 1000L);
      }
      HttpOutcome outcome;
      try {
        outcome = restClient
            .post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header("X-Api-Key", credentials.apiKey())
            .body(payload)
            .exchange(
                (req, res) -> new HttpOutcome(
                    res.getStatusCode().value(),
                    new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
      } catch (Exception e) {
        log.error("Bictorys create charge failed: {}", e.getMessage());
        throw new BusinessRuleException(
            "Impossible de contacter Bictorys. Réessayez ou contactez le support.");
      }

      if (outcome.status() >= 200 && outcome.status() < 300) {
        return parseCharge(outcome.body());
      }
      if (isWafBlock(outcome) && attempt < MAX_RETRIES) {
        log.warn("Bictorys WAF 403, retrying (attempt {})", attempt + 1);
        continue;
      }
      log.error("Bictorys charge error {}: {}", outcome.status(), outcome.body());
      throw new BusinessRuleException(
          "Bictorys a refusé la création du paiement: " + errorMessage(outcome));
    }
    throw new BusinessRuleException("Bictorys indisponible. Réessayez dans quelques instants.");
  }

  /** Platform account (subscriptions). */
  public BictorysStatusResult getStatus(String transactionId) {
    requireEnabled();
    return getStatus(platformCredentials(), transactionId);
  }

  public BictorysStatusResult getStatus(BictorysCredentials credentials, String transactionId) {
    requireApiKey(credentials);
    String url = apiRoot(credentials) + "/pay/v1/transactions/" + transactionId + "/status";
    try {
      String raw = restClient
          .get()
          .uri(url)
          .accept(MediaType.APPLICATION_JSON)
          .header("X-Api-Key", credentials.apiKey())
          .retrieve()
          .body(String.class);
      JsonNode root = objectMapper.readTree(raw);
      return new BictorysStatusResult(text(root, "status"), intOrNull(root, "amount"));
    } catch (Exception e) {
      log.warn("Bictorys status failed for {}: {}", transactionId, e.getMessage());
      throw new BusinessRuleException("Impossible de vérifier le statut du paiement Bictorys");
    }
  }

  /**
   * HMAC-SHA256 of {@code timestamp.rawBody} when signature headers are sent,
   * otherwise constant-time comparison of {@code X-Secret-Key}.
   */
  public boolean verifyWebhook(
      String rawBody, String secretKeyHeader, String signature, String timestamp) {
    return verifyWebhook(
        properties.getWebhookSecret(), rawBody, secretKeyHeader, signature, timestamp);
  }

  public boolean verifyWebhook(
      BictorysCredentials credentials,
      String rawBody,
      String secretKeyHeader,
      String signature,
      String timestamp) {
    return verifyWebhook(
        credentials.webhookSecret(), rawBody, secretKeyHeader, signature, timestamp);
  }

  /**
   * Checks that the API key is accepted by probing the status of an unknown
   * transaction: an auth failure means a bad key, a 404 means the key works.
   */
  public boolean isApiKeyAccepted(BictorysCredentials credentials) {
    requireApiKey(credentials);
    String url = apiRoot(credentials)
        + "/pay/v1/transactions/"
        + UUID.randomUUID()
        + "/status";
    HttpOutcome outcome;
    try {
      outcome = restClient
          .get()
          .uri(url)
          .accept(MediaType.APPLICATION_JSON)
          .header("X-Api-Key", credentials.apiKey())
          .exchange(
              (req, res) -> new HttpOutcome(
                  res.getStatusCode().value(),
                  new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    } catch (Exception e) {
      log.warn("Bictorys key check failed: {}", e.getMessage());
      throw new BusinessRuleException("Impossible de contacter Bictorys. Réessayez plus tard.");
    }
    if (outcome.status() == 401) {
      return false;
    }
    return !(outcome.status() == 403 && !isWafBlock(outcome));
  }

  private boolean verifyWebhook(
      String secret,
      String rawBody,
      String secretKeyHeader,
      String signature,
      String timestamp) {
    if (isBlank(secret)) {
      log.error("Bictorys webhook secret not configured");
      return false;
    }
    if (!isBlank(signature) && !isBlank(timestamp)) {
      long ts;
      try {
        ts = Long.parseLong(timestamp.trim());
      } catch (NumberFormatException e) {
        return false;
      }
      if (Math.abs(System.currentTimeMillis() - ts) > WEBHOOK_MAX_SKEW_MS) {
        return false;
      }
      String expected = hmacSha256Hex(secret, timestamp.trim() + "." + (rawBody != null ? rawBody : ""));
      return constantTimeEquals(expected, signature.trim().toLowerCase());
    }
    if (!isBlank(secretKeyHeader)) {
      return constantTimeEquals(secret, secretKeyHeader.trim());
    }
    return false;
  }

  public static String toPaymentType(String channel) {
    if (channel == null) {
      return null;
    }
    return switch (channel.toLowerCase()) {
      case "wave" -> "wave_money";
      case "orange_money" -> "orange_money";
      default -> null;
    };
  }

  /**
   * Bictorys requires {@code +<country code><number>} without spaces. Local
   * Senegalese numbers (9 digits) are prefixed with +221.
   */
  public static String normalizePhone(String raw) {
    if (isBlank(raw)) {
      return null;
    }
    String digits = raw.replaceAll("[^0-9+]", "");
    if (digits.startsWith("00")) {
      digits = "+" + digits.substring(2);
    }
    if (digits.startsWith("+")) {
      String rest = digits.substring(1).replace("+", "");
      return rest.length() >= 8 && rest.length() <= 15 ? "+" + rest : null;
    }
    if (digits.length() == 9) {
      return "+221" + digits;
    }
    if (digits.startsWith("221") && digits.length() == 12) {
      return "+" + digits;
    }
    return null;
  }

  static boolean isPublicUrl(String url) {
    if (isBlank(url)) {
      return false;
    }
    URI uri;
    try {
      uri = URI.create(url.trim());
    } catch (IllegalArgumentException e) {
      return false;
    }
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (host == null
        || scheme == null
        || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      return false;
    }
    host = host.toLowerCase();
    if (host.startsWith("[") && host.endsWith("]")) {
      host = host.substring(1, host.length() - 1);
    }
    if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
      return false;
    }
    boolean isIpLiteral = host.contains(":") || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    if (!isIpLiteral) {
      return true;
    }
    try {
      InetAddress addr = InetAddress.getByName(host);
      return !(addr.isLoopbackAddress()
          || addr.isSiteLocalAddress()
          || addr.isLinkLocalAddress()
          || addr.isAnyLocalAddress());
    } catch (Exception e) {
      return false;
    }
  }

  private BictorysChargeResult parseCharge(String raw) {
    try {
      JsonNode root = objectMapper.readTree(raw);
      String transactionId = text(root, "transactionId");
      if (transactionId == null) {
        transactionId = text(root, "id");
      }
      if (isBlank(transactionId)) {
        throw new BusinessRuleException("Réponse Bictorys invalide (transactionId manquant)");
      }
      return new BictorysChargeResult(
          transactionId,
          text(root, "redirectUrl"),
          text(root, "link"),
          text(root, "qrCode"),
          text(root, "message"));
    } catch (BusinessRuleException e) {
      throw e;
    } catch (Exception e) {
      log.error("Bictorys charge response unreadable: {}", raw);
      throw new BusinessRuleException("Réponse Bictorys invalide");
    }
  }

  private String errorMessage(HttpOutcome outcome) {
    try {
      JsonNode root = objectMapper.readTree(outcome.body());
      String msg = text(root, "message");
      if (msg == null) {
        msg = text(root, "error");
      }
      if (msg != null) {
        return msg;
      }
    } catch (Exception ignored) {
      // non-JSON body (WAF HTML page)
    }
    return "HTTP " + outcome.status();
  }

  private static boolean isWafBlock(HttpOutcome outcome) {
    if (outcome.status() != 403) {
      return false;
    }
    String b = outcome.body() == null ? "" : outcome.body().trim();
    return b.startsWith("<") || b.contains("Forbidden");
  }

  private void requireEnabled() {
    if (!properties.isEnabled()) {
      throw new BusinessRuleException(
          "Paiement en ligne désactivé. Contactez le support pour activer votre abonnement.");
    }
    if (isBlank(properties.getApiKey())) {
      throw new BusinessRuleException("Configuration Bictorys incomplète");
    }
  }

  private static void requireApiKey(BictorysCredentials credentials) {
    if (credentials == null || isBlank(credentials.apiKey())) {
      throw new BusinessRuleException("Configuration Bictorys incomplète");
    }
  }

  private BictorysCredentials platformCredentials() {
    return BictorysCredentials.fromProperties(properties);
  }

  private static String apiRoot(BictorysCredentials credentials) {
    String base = isBlank(credentials.baseUrl())
        ? BictorysCredentials.TEST_BASE_URL
        : credentials.baseUrl();
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }

  static String hmacSha256Hex(String secret, String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("HmacSHA256 unavailable", e);
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
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

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private record HttpOutcome(int status, String body) {
  }
}
