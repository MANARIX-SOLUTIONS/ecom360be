package com.ecom360.tenant.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.application.dto.CancelSubscriptionRequest;
import com.ecom360.tenant.application.dto.ChangePlanRequest;
import com.ecom360.tenant.application.dto.CheckoutSessionResponse;
import com.ecom360.tenant.application.dto.CreateCheckoutRequest;
import com.ecom360.tenant.application.dto.PaymentTransactionResponse;
import com.ecom360.tenant.application.dto.PlanResponse;
import com.ecom360.tenant.application.dto.SubscriptionResponse;
import com.ecom360.tenant.application.dto.SubscriptionUsageResponse;
import com.ecom360.tenant.application.service.SubscriptionPaymentService;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.application.service.SubscriptionUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/subscription")
@Tag(name = "Subscription", description = "Subscription and plan management")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

  private final SubscriptionService subscriptionService;
  private final SubscriptionUsageService subscriptionUsageService;
  private final SubscriptionPaymentService subscriptionPaymentService;

  public SubscriptionController(
      SubscriptionService subscriptionService,
      SubscriptionUsageService subscriptionUsageService,
      SubscriptionPaymentService subscriptionPaymentService) {
    this.subscriptionService = subscriptionService;
    this.subscriptionUsageService = subscriptionUsageService;
    this.subscriptionPaymentService = subscriptionPaymentService;
  }

  @GetMapping("/usage")
  @Operation(summary = "Get current usage vs plan limits")
  public ResponseEntity<SubscriptionUsageResponse> getUsage(
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(subscriptionUsageService.getUsage(p));
  }

  @GetMapping("/me")
  @Operation(summary = "Get current subscription")
  public ResponseEntity<SubscriptionResponse> getCurrent(@AuthenticationPrincipal UserPrincipal p) {
    return subscriptionService
        .getCurrent(p)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.noContent().build());
  }

  @GetMapping("/plans")
  @Operation(summary = "List available plans")
  public ResponseEntity<List<PlanResponse>> listPlans(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(subscriptionService.listPlans(p));
  }

  @PostMapping("/change")
  @Operation(summary = "Deprecated: use checkout to change plan")
  public ResponseEntity<SubscriptionResponse> changePlan(
      @Valid @RequestBody ChangePlanRequest req, @AuthenticationPrincipal UserPrincipal p) {
    throw new BusinessRuleException(
        "Le changement de plan doit passer par le paiement PSP via /subscription/checkout.");
  }

  @PostMapping("/checkout")
  @Operation(summary = "Create a PSP checkout session for a subscription")
  public ResponseEntity<CheckoutSessionResponse> createCheckout(
      @Valid @RequestBody CreateCheckoutRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        subscriptionPaymentService.createCheckout(
            req.planSlug(), req.billingCycle(), req.paymentMethod(), p));
  }

  @GetMapping("/payments/{transactionId}")
  @Operation(summary = "Get subscription payment transaction status")
  public ResponseEntity<PaymentTransactionResponse> getPayment(
      @PathVariable UUID transactionId, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(subscriptionPaymentService.getTransaction(transactionId, p));
  }

  @PostMapping("/cancel")
  @Operation(summary = "Cancel subscription (at period end by default)")
  public ResponseEntity<Void> cancel(
      @RequestBody(required = false) CancelSubscriptionRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    Boolean atPeriodEnd = req != null ? req.atPeriodEnd() : Boolean.TRUE;
    subscriptionService.cancelSubscription(atPeriodEnd, p);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/reactivate")
  @Operation(summary = "Reactivate cancelled subscription (before period end)")
  public ResponseEntity<SubscriptionResponse> reactivate(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(subscriptionService.reactivateSubscription(p));
  }
}
