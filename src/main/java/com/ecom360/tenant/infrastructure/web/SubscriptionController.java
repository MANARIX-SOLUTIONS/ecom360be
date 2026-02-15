package com.ecom360.tenant.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.application.dto.ChangePlanRequest;
import com.ecom360.tenant.application.dto.PlanResponse;
import com.ecom360.tenant.application.dto.SubscriptionResponse;
import com.ecom360.tenant.application.dto.SubscriptionUsageResponse;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.application.service.SubscriptionUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

  public SubscriptionController(
      SubscriptionService subscriptionService, SubscriptionUsageService subscriptionUsageService) {
    this.subscriptionService = subscriptionService;
    this.subscriptionUsageService = subscriptionUsageService;
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
  @Operation(summary = "Change plan")
  public ResponseEntity<SubscriptionResponse> changePlan(
      @Valid @RequestBody ChangePlanRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(subscriptionService.changePlan(req.planSlug(), req.billingCycle(), p));
  }

  @PostMapping("/cancel")
  @Operation(summary = "Cancel subscription")
  public ResponseEntity<Void> cancel(@AuthenticationPrincipal UserPrincipal p) {
    subscriptionService.cancelSubscription(p);
    return ResponseEntity.noContent().build();
  }
}
