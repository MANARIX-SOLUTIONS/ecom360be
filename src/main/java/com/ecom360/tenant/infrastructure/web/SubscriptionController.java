package com.ecom360.tenant.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.application.dto.CancelSubscriptionRequest;
import com.ecom360.tenant.application.dto.ChangePlanRequest;
import com.ecom360.tenant.application.dto.PlanResponse;
import com.ecom360.tenant.application.dto.SubscriptionResponse;
import com.ecom360.tenant.application.dto.SubscriptionUsageResponse;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.application.service.SubscriptionUsageService;
import com.ecom360.tenant.payment.application.dto.CreateSubscriptionCheckoutRequest;
import com.ecom360.tenant.payment.application.dto.SubscriptionCheckoutResponse;
import com.ecom360.tenant.payment.application.service.SubscriptionCheckoutService;
import com.ecom360.shared.application.dto.PageResponse;
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
  private final SubscriptionCheckoutService subscriptionCheckoutService;

  public SubscriptionController(
      SubscriptionService subscriptionService,
      SubscriptionUsageService subscriptionUsageService,
      SubscriptionCheckoutService subscriptionCheckoutService) {
    this.subscriptionService = subscriptionService;
    this.subscriptionUsageService = subscriptionUsageService;
    this.subscriptionCheckoutService = subscriptionCheckoutService;
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
  @Operation(summary = "Change plan (disabled)", description = "Use POST /subscription/checkout — activation only after payment.")
  public ResponseEntity<SubscriptionResponse> changePlan(
      @Valid @RequestBody ChangePlanRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(subscriptionService.changePlan(req.planSlug(), req.billingCycle(), p));
  }

  @PostMapping("/checkout")
  @Operation(summary = "Start paid subscription checkout (Wave / Orange Money via PayDunya)")
  public ResponseEntity<SubscriptionCheckoutResponse> checkout(
      @Valid @RequestBody CreateSubscriptionCheckoutRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(subscriptionCheckoutService.createCheckout(req, p));
  }

  @GetMapping("/checkout/{intentId}")
  @Operation(summary = "Get checkout / payment intent status")
  public ResponseEntity<SubscriptionCheckoutResponse> checkoutStatus(
      @PathVariable UUID intentId, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(subscriptionCheckoutService.getCheckoutStatus(intentId, p));
  }

  @GetMapping("/payments")
  @Operation(summary = "List subscription payment history for current business")
  public ResponseEntity<PageResponse<SubscriptionCheckoutResponse>> payments(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        subscriptionCheckoutService.listTenantPayments(
            p, page, Math.min(size, ApiConstants.MAX_PAGE_SIZE)));
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
