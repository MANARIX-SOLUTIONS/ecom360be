package com.ecom360.tenant.infrastructure.payment;

import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.application.payment.CheckoutCreateRequest;
import com.ecom360.tenant.application.payment.CheckoutCreateResponse;
import com.ecom360.tenant.application.payment.PaymentCallbackResult;
import com.ecom360.tenant.application.payment.PaymentProvider;
import com.ecom360.tenant.domain.model.PaymentTransactionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PayDunyaPaymentProvider implements PaymentProvider {

  public static final String PROVIDER_NAME = "paydunya";

  private final PayDunyaProperties payDunyaProperties;
  private final PaymentProperties paymentProperties;
  private final ObjectMapper objectMapper;

  public PayDunyaPaymentProvider(
      PayDunyaProperties payDunyaProperties,
      PaymentProperties paymentProperties,
      ObjectMapper objectMapper) {
    this.payDunyaProperties = payDunyaProperties;
    this.paymentProperties = paymentProperties;
    this.objectMapper = objectMapper;
  }

  @Override
  public String getName() {
    return PROVIDER_NAME;
  }

  @Override
  public CheckoutCreateResponse createCheckout(CheckoutCreateRequest request) {
    ensureConfigured();

    Map<String, Object> body = new LinkedHashMap<>();
    body.put(
        "invoice",
        Map.of(
            "total_amount",
            request.amount(),
            "description",
            "Abonnement " + request.planName() + " - " + request.billingCycle()));
    body.put("store", Map.of("name", payDunyaProperties.getStoreName()));
    body.put(
        "custom_data",
        Map.of(
            "transaction_id",
            request.transactionId().toString(),
            "business_id",
            request.businessId().toString(),
            "subscription_id",
            request.subscriptionId().toString(),
            "invoice_number",
            request.invoiceNumber(),
            "plan_slug",
            request.planSlug(),
            "billing_cycle",
            request.billingCycle()));
    body.put(
        "actions",
        Map.of(
            "callback_url",
            paymentProperties.getPublicApiUrl()
                + ApiConstants.API_BASE
                + "/public/payments/paydunya/webhook",
            "return_url",
            paymentProperties.getAppUrl()
                + "/settings/subscription?payment=success&transactionId="
                + request.transactionId(),
            "cancel_url",
            paymentProperties.getAppUrl()
                + "/settings/subscription?payment=cancelled&transactionId="
                + request.transactionId()));

    try {
      JsonNode response = restClient()
          .post()
          .uri("/api/v1/checkout-invoice/create")
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(JsonNode.class);
      if (response == null || !"00".equals(response.path("response_code").asText())) {
        throw new BusinessRuleException("PayDunya n'a pas pu créer la facture de paiement.");
      }
      String checkoutUrl = response.path("response_text").asText(null);
      String token = response.path("token").asText(null);
      if (checkoutUrl == null || token == null) {
        throw new BusinessRuleException("Réponse PayDunya incomplète.");
      }
      return new CheckoutCreateResponse(token, checkoutUrl);
    } catch (RestClientException e) {
      throw new BusinessRuleException(
          "PayDunya est indisponible. Réessayez dans quelques instants.");
    }
  }

  @Override
  public PaymentCallbackResult parseCallback(String rawPayload) {
    ensureConfigured();
    try {
      JsonNode root = objectMapper.readTree(rawPayload);
      JsonNode data = root.has("data") ? root.path("data") : root;
      String hash = data.path("hash").asText("");
      if (!sha512(payDunyaProperties.getMasterKey()).equalsIgnoreCase(hash)) {
        throw new AccessDeniedException("Signature PayDunya invalide");
      }

      String providerReference = data.path("invoice").path("token").asText(null);
      if (providerReference == null || providerReference.isBlank()) {
        providerReference = data.path("token").asText(null);
      }
      if (providerReference == null || providerReference.isBlank()) {
        throw new BusinessRuleException("Callback PayDunya sans référence de paiement.");
      }

      String providerStatus = data.path("status").asText("").toLowerCase();
      String status = switch (providerStatus) {
        case "completed" -> PaymentTransactionStatus.PAID;
        case "cancelled", "canceled" -> PaymentTransactionStatus.CANCELLED;
        case "failed" -> PaymentTransactionStatus.FAILED;
        default -> PaymentTransactionStatus.PENDING;
      };
      String failureReason = data.path("fail_reason").asText(null);
      return new PaymentCallbackResult(providerReference, status, failureReason, rawPayload);
    } catch (AccessDeniedException | BusinessRuleException e) {
      throw e;
    } catch (JsonProcessingException e) {
      throw new BusinessRuleException("Payload PayDunya invalide.");
    }
  }

  private RestClient restClient() {
    return RestClient.builder()
        .baseUrl(payDunyaProperties.getBaseUrl())
        .defaultHeader("PAYDUNYA-MASTER-KEY", payDunyaProperties.getMasterKey())
        .defaultHeader("PAYDUNYA-PRIVATE-KEY", payDunyaProperties.getPrivateKey())
        .defaultHeader("PAYDUNYA-TOKEN", payDunyaProperties.getToken())
        .build();
  }

  private void ensureConfigured() {
    if (!payDunyaProperties.isEnabled()
        || isBlank(payDunyaProperties.getMasterKey())
        || isBlank(payDunyaProperties.getPrivateKey())
        || isBlank(payDunyaProperties.getToken())) {
      throw new BusinessRuleException("PayDunya n'est pas configuré pour les paiements.");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String sha512(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-512");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-512 unavailable", e);
    }
  }
}
