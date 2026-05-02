package com.ecom360.tenant.infrastructure.web;

import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.application.service.SubscriptionPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/public/payments")
@Tag(name = "Public payments", description = "Public PSP payment callbacks")
public class PublicPaymentWebhookController {

  private final SubscriptionPaymentService subscriptionPaymentService;

  public PublicPaymentWebhookController(SubscriptionPaymentService subscriptionPaymentService) {
    this.subscriptionPaymentService = subscriptionPaymentService;
  }

  @PostMapping("/paydunya/webhook")
  @Operation(summary = "Receive PayDunya subscription payment callback")
  public ResponseEntity<Void> handlePayDunyaWebhook(@RequestBody String rawPayload) {
    subscriptionPaymentService.processProviderCallback("paydunya", rawPayload);
    return ResponseEntity.noContent().build();
  }
}
