package com.ecom360.tenant.payment.infrastructure.web;

import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.payment.application.service.SubscriptionCheckoutService;
import com.ecom360.tenant.payment.domain.PaymentIntentNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/public/payments/paydunya")
@Hidden
public class PublicPaydunyaWebhookController {

  private static final Logger log = LoggerFactory.getLogger(PublicPaydunyaWebhookController.class);

  private final SubscriptionCheckoutService checkoutService;
  private final ObjectMapper objectMapper;

  public PublicPaydunyaWebhookController(
      SubscriptionCheckoutService checkoutService, ObjectMapper objectMapper) {
    this.checkoutService = checkoutService;
    this.objectMapper = objectMapper;
  }

  /**
   * PayDunya IPN: typically {@code application/x-www-form-urlencoded} with a
   * {@code data} field
   * containing JSON. Also accepts raw JSON body with a {@code data} node.
   *
   * <p>
   * Unknown intent → 503 so the PSP can retry. Invalid hash → 401.
   */
  @PostMapping(value = "/ipn", consumes = {
      MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      MediaType.APPLICATION_JSON_VALUE,
      MediaType.ALL_VALUE
  })
  public ResponseEntity<Map<String, String>> ipn(
      @RequestParam(value = "data", required = false) String dataParam,
      @RequestBody(required = false) String rawBody) {
    try {
      JsonNode dataNode = extractDataNode(dataParam, rawBody);
      checkoutService.handlePaydunyaIpn(dataNode);
      return ResponseEntity.ok(Map.of("status", "ok"));
    } catch (PaymentIntentNotFoundException e) {
      log.warn("PayDunya IPN intent missing (will retry): {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("status", "retry", "message", e.getMessage()));
    } catch (AccessDeniedException e) {
      log.warn("PayDunya IPN auth failed: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("status", "error", "message", e.getMessage()));
    } catch (Exception e) {
      log.warn("PayDunya IPN rejected: {}", e.getMessage());
      return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
    }
  }

  private JsonNode extractDataNode(String dataParam, String rawBody) throws Exception {
    if (dataParam != null && !dataParam.isBlank()) {
      return objectMapper.readTree(dataParam);
    }
    if (rawBody != null && !rawBody.isBlank()) {
      JsonNode root = objectMapper.readTree(rawBody);
      if (root.has("data")) {
        JsonNode data = root.get("data");
        if (data.isTextual()) {
          return objectMapper.readTree(data.asText());
        }
        return data;
      }
      return root;
    }
    throw new IllegalArgumentException("Missing PayDunya IPN payload");
  }
}
