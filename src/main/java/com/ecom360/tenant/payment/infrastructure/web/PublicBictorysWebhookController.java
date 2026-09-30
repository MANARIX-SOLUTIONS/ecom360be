package com.ecom360.tenant.payment.infrastructure.web;

import com.ecom360.sales.application.service.PosDigitalCheckoutService;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.payment.application.service.BusinessPaymentProviderService;
import com.ecom360.tenant.payment.application.service.SubscriptionCheckoutService;
import com.ecom360.tenant.payment.domain.PaymentIntentNotFoundException;
import com.ecom360.tenant.payment.domain.model.BusinessPaymentProvider;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/public/payments/bictorys")
@Hidden
public class PublicBictorysWebhookController {

  private static final Logger log = LoggerFactory.getLogger(PublicBictorysWebhookController.class);

  private final SubscriptionCheckoutService checkoutService;
  private final PosDigitalCheckoutService posCheckoutService;
  private final BusinessPaymentProviderService paymentProviderService;
  private final BictorysClient bictorysClient;
  private final ObjectMapper objectMapper;

  public PublicBictorysWebhookController(
      SubscriptionCheckoutService checkoutService,
      PosDigitalCheckoutService posCheckoutService,
      BusinessPaymentProviderService paymentProviderService,
      BictorysClient bictorysClient,
      ObjectMapper objectMapper) {
    this.checkoutService = checkoutService;
    this.posCheckoutService = posCheckoutService;
    this.paymentProviderService = paymentProviderService;
    this.bictorysClient = bictorysClient;
    this.objectMapper = objectMapper;
  }

  /**
   * Bictorys retries on non-200 responses, so everything except an unknown
   * intent (possible race with checkout creation) is acknowledged with 200.
   * The raw body is required to validate the HMAC signature.
   */
  @PostMapping("/webhook")
  public ResponseEntity<Map<String, Object>> webhook(
      @RequestBody(required = false) String rawBody,
      @RequestHeader(value = "X-Secret-Key", required = false) String secretKey,
      @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
      @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp) {
    if (!bictorysClient.verifyWebhook(rawBody, secretKey, signature, timestamp)) {
      log.warn("Bictorys webhook rejected: invalid signature");
      return ResponseEntity.ok(Map.of("received", true));
    }
    try {
      JsonNode payload = objectMapper.readTree(rawBody);
      checkoutService.handleBictorysWebhook(payload);
    } catch (PaymentIntentNotFoundException e) {
      log.warn("Bictorys webhook intent missing (will retry): {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("received", false, "message", e.getMessage()));
    } catch (Exception e) {
      log.error("Bictorys webhook processing failed: {}", e.getMessage());
    }
    return ResponseEntity.ok(Map.of("received", true));
  }

  /**
   * POS payments land on each business's own Bictorys account; the token in the
   * URL selects the account whose secret must sign the payload.
   */
  @PostMapping("/pos/{webhookToken}")
  public ResponseEntity<Map<String, Object>> posWebhook(
      @PathVariable String webhookToken,
      @RequestBody(required = false) String rawBody,
      @RequestHeader(value = "X-Secret-Key", required = false) String secretKey,
      @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
      @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp) {
    BusinessPaymentProvider account =
        paymentProviderService.findByWebhookToken(webhookToken).orElse(null);
    if (account == null) {
      log.warn("POS Bictorys webhook for unknown token — ignored");
      return ResponseEntity.ok(Map.of("received", true));
    }
    if (!bictorysClient.verifyWebhook(
        paymentProviderService.credentials(account), rawBody, secretKey, signature, timestamp)) {
      log.warn("POS Bictorys webhook rejected: invalid signature (business={})", account.getBusinessId());
      return ResponseEntity.ok(Map.of("received", true));
    }
    try {
      JsonNode payload = objectMapper.readTree(rawBody);
      posCheckoutService.handleWebhook(account, payload);
    } catch (PaymentIntentNotFoundException e) {
      log.warn("POS Bictorys webhook intent missing (will retry): {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("received", false, "message", e.getMessage()));
    } catch (Exception e) {
      log.error("POS Bictorys webhook processing failed: {}", e.getMessage());
    }
    return ResponseEntity.ok(Map.of("received", true));
  }
}
