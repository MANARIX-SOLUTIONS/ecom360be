package com.ecom360.tenant.payment.infrastructure.paydunya;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.tenant.payment.infrastructure.config.PaydunyaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaydunyaClient {

  private static final Logger log = LoggerFactory.getLogger(PaydunyaClient.class);

  private final PaydunyaProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public PaydunyaClient(PaydunyaProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient = RestClient.builder().build();
  }

  public PaydunyaCheckoutResult createCheckoutInvoice(
      int amount,
      String description,
      String channel,
      UUID intentId,
      String returnUrl,
      String cancelUrl,
      String customerName,
      String customerEmail,
      String customerPhone) {
    requireEnabled();

    String paydunyaChannel = toPaydunyaChannel(channel);
    Map<String, Object> invoice = new LinkedHashMap<>();
    invoice.put("total_amount", amount);
    invoice.put("description", description);
    if (paydunyaChannel != null) {
      invoice.put("channels", List.of(paydunyaChannel));
    }
    Map<String, String> customer = new LinkedHashMap<>();
    if (customerName != null && !customerName.isBlank()) {
      customer.put("name", customerName);
    }
    if (customerEmail != null && !customerEmail.isBlank()) {
      customer.put("email", customerEmail);
    }
    if (customerPhone != null && !customerPhone.isBlank()) {
      customer.put("phone", customerPhone);
    }
    if (!customer.isEmpty()) {
      invoice.put("customer", customer);
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("invoice", invoice);
    body.put("store", Map.of("name", properties.getStoreName()));
    body.put(
        "custom_data",
        Map.of(
            "intent_id", intentId.toString(),
            "channel", channel));
    body.put(
        "actions",
        Map.of(
            "callback_url", callbackUrl(),
            "return_url", returnUrl,
            "cancel_url", cancelUrl != null ? cancelUrl : returnUrl));

    String url = apiRoot() + "/checkout-invoice/create";
    try {
      String raw = restClient
          .post()
          .uri(url)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(this::authHeaders)
          .body(body)
          .retrieve()
          .body(String.class);
      JsonNode root = objectMapper.readTree(raw);
      String code = text(root, "response_code");
      if (!"00".equals(code)) {
        String msg = text(root, "response_text");
        throw new BusinessRuleException(
            "PayDunya a refusé la création du paiement: "
                + (msg != null ? msg : "code " + code));
      }
      String token = text(root, "token");
      String checkoutUrl = text(root, "response_text");
      if (token == null || token.isBlank() || checkoutUrl == null || checkoutUrl.isBlank()) {
        throw new BusinessRuleException("Réponse PayDunya invalide (token/url manquants)");
      }
      return new PaydunyaCheckoutResult(token, checkoutUrl);
    } catch (BusinessRuleException e) {
      throw e;
    } catch (Exception e) {
      log.error("PayDunya create checkout failed: {}", e.getMessage());
      throw new BusinessRuleException(
          "Impossible de contacter PayDunya. Réessayez ou contactez le support.");
    }
  }

  public PaydunyaConfirmResult confirmInvoice(String token) {
    requireEnabled();
    String url = apiRoot() + "/checkout-invoice/confirm/" + token;
    try {
      String raw = restClient
          .get()
          .uri(url)
          .headers(this::authHeaders)
          .retrieve()
          .body(String.class);
      JsonNode root = objectMapper.readTree(raw);
      String status = text(root, "status");
      JsonNode invoiceNode = root.path("invoice");
      if (status == null && invoiceNode.isObject()) {
        status = text(invoiceNode, "status");
      }
      Integer totalAmount = intOrNull(root, "total_amount");
      if (totalAmount == null && invoiceNode.isObject()) {
        totalAmount = intOrNull(invoiceNode, "total_amount");
      }
      return new PaydunyaConfirmResult(
          status, text(root, "hash"), text(root, "fail_reason"), totalAmount);
    } catch (BusinessRuleException e) {
      throw e;
    } catch (Exception e) {
      log.warn("PayDunya confirm failed for token {}: {}", token, e.getMessage());
      throw new BusinessRuleException("Impossible de vérifier le statut du paiement PayDunya");
    }
  }

  public boolean verifyMasterKeyHash(String receivedHash) {
    if (receivedHash == null || receivedHash.isBlank()) {
      return false;
    }
    String expected = sha512Hex(properties.getMasterKey());
    return expected.equalsIgnoreCase(receivedHash.trim());
  }

  public String toPaydunyaChannel(String channel) {
    if (channel == null) {
      return null;
    }
    return switch (channel.toLowerCase()) {
      case "wave" -> "wave-senegal";
      case "orange_money" -> "orange-money-senegal";
      default -> null;
    };
  }

  private void requireEnabled() {
    if (!properties.isEnabled()) {
      throw new BusinessRuleException(
          "Paiement en ligne désactivé. Contactez le support pour activer votre abonnement.");
    }
    if (isBlank(properties.getMasterKey())
        || isBlank(properties.getPrivateKey())
        || isBlank(properties.getToken())) {
      throw new BusinessRuleException("Configuration PayDunya incomplète");
    }
  }

  private String callbackUrl() {
    String base = properties.getApiPublicUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/api/v1/public/payments/paydunya/ipn";
  }

  private String apiRoot() {
    String base = properties.getBaseUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    // sandbox: .../sandbox-api/v1 ; prod: .../api/v1
    if (base.contains("/api/v1") || base.contains("/sandbox-api/v1")) {
      return base;
    }
    if (base.contains("sandbox")) {
      return base + "/sandbox-api/v1";
    }
    return base + "/api/v1";
  }

  private void authHeaders(org.springframework.http.HttpHeaders headers) {
    headers.set("PAYDUNYA-MASTER-KEY", properties.getMasterKey());
    headers.set("PAYDUNYA-PRIVATE-KEY", properties.getPrivateKey());
    headers.set("PAYDUNYA-TOKEN", properties.getToken());
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
      return Integer.parseInt(n.asText().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  static String sha512Hex(String value) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-512");
      byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-512 unavailable", e);
    }
  }
}
