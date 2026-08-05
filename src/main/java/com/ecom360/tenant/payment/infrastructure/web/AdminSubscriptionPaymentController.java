package com.ecom360.tenant.payment.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.payment.application.dto.AdminSubscriptionPaymentResponse;
import com.ecom360.tenant.payment.application.dto.MarkPaidRequest;
import com.ecom360.tenant.payment.application.service.SubscriptionCheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/admin/subscription-payments")
@Tag(name = "Admin Subscription Payments", description = "Historique paiements abonnements")
@SecurityRequirement(name = "bearerAuth")
public class AdminSubscriptionPaymentController {

  private final SubscriptionCheckoutService checkoutService;

  public AdminSubscriptionPaymentController(SubscriptionCheckoutService checkoutService) {
    this.checkoutService = checkoutService;
  }

  @GetMapping
  @Operation(summary = "List subscription payment intents")
  public ResponseEntity<PageResponse<AdminSubscriptionPaymentResponse>> list(
      @RequestParam(required = false) UUID businessId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    Instant fromI = SubscriptionCheckoutService.parseInstantOrDate(from, false);
    Instant toI = SubscriptionCheckoutService.parseInstantOrDate(to, true);
    return ResponseEntity.ok(
        checkoutService.listAdminPayments(
            businessId,
            status,
            fromI,
            toI,
            page,
            Math.min(size, ApiConstants.MAX_PAGE_SIZE)));
  }

  @PostMapping("/{intentId}/mark-paid")
  @Operation(summary = "Mark a pending payment as paid (support bypass)")
  public ResponseEntity<AdminSubscriptionPaymentResponse> markPaid(
      @PathVariable UUID intentId,
      @RequestBody(required = false) MarkPaidRequest body,
      @AuthenticationPrincipal UserPrincipal p) {
    String note = body != null ? body.note() : null;
    return ResponseEntity.ok(checkoutService.markPaid(intentId, note, p));
  }
}
