package com.ecom360.admin.infrastructure.web;

import com.ecom360.admin.application.dto.AdminAssignPlanRequest;
import com.ecom360.admin.application.dto.AdminRenewSubscriptionRequest;
import com.ecom360.admin.application.dto.AdminBusinessResponse;
import com.ecom360.admin.application.dto.AdminCreateBusinessRequest;
import com.ecom360.admin.application.dto.AdminPlanItem;
import com.ecom360.admin.application.dto.AdminUpdateBusinessRequest;
import com.ecom360.admin.application.service.AdminBusinessService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.tenant.application.dto.SubscriptionUsageResponse;
import com.ecom360.tenant.application.service.SubscriptionUsageService;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/admin/businesses")
@Tag(name = "Admin Businesses", description = "Platform admin: list and manage businesses")
@SecurityRequirement(name = "bearerAuth")
public class AdminBusinessController {

  private final AdminBusinessService adminBusinessService;
  private final SubscriptionUsageService subscriptionUsageService;

  public AdminBusinessController(
      AdminBusinessService adminBusinessService,
      SubscriptionUsageService subscriptionUsageService) {
    this.adminBusinessService = adminBusinessService;
    this.subscriptionUsageService = subscriptionUsageService;
  }

  @GetMapping
  @Operation(summary = "List all businesses (platform admin)")
  public ResponseEntity<PageResponse<AdminBusinessResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String plan,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(
            adminBusinessService.list(
                p, page, Math.min(size, ApiConstants.MAX_PAGE_SIZE), search, status, plan)));
  }

  @GetMapping("/plans")
  @Operation(summary = "List available plans (for create business / assign plan)")
  public ResponseEntity<List<AdminPlanItem>> listPlans(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(adminBusinessService.listPlansForAdmin(p));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get business by id (platform admin)")
  public ResponseEntity<AdminBusinessResponse> getById(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(adminBusinessService.getById(id, p));
  }

  @GetMapping("/{id}/subscription/usage")
  @Operation(summary = "Usage vs limites du plan pour une entreprise (admin)")
  public ResponseEntity<SubscriptionUsageResponse> getSubscriptionUsage(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(subscriptionUsageService.getUsageForBusiness(id, p));
  }

  @PostMapping
  @Operation(summary = "Create (subscribe) a new business (platform admin)")
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<AdminBusinessResponse> create(
      @Valid @RequestBody AdminCreateBusinessRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    AdminBusinessResponse created = adminBusinessService.create(req, p);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update business (platform admin)")
  public ResponseEntity<AdminBusinessResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody AdminUpdateBusinessRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(adminBusinessService.update(id, req, p));
  }

  @PatchMapping("/{id}/plan")
  @Operation(summary = "Assign or change plan for a business (platform admin)")
  public ResponseEntity<Void> assignPlan(
      @PathVariable UUID id,
      @Valid @RequestBody AdminAssignPlanRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    String cycle =
        req.billingCycle() != null && !req.billingCycle().isBlank()
            ? req.billingCycle()
            : "monthly";
    adminBusinessService.assignPlan(id, req.planSlug(), cycle, p);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/subscription/renew")
  @Operation(
      summary = "Renouveler l'abonnement (admin)",
      description =
          "Ajoute une période de facturation : empilée après la fin de période si abonnement payant"
              + " actif ; sinon à partir d'aujourd'hui. Essai → passage payant immédiat.")
  public ResponseEntity<Void> renewSubscription(
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) AdminRenewSubscriptionRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    adminBusinessService.renewSubscription(id, req, p);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/status")
  @Operation(summary = "Update business status (suspend/activate)")
  public ResponseEntity<Void> setStatus(
      @PathVariable UUID id,
      @RequestBody Map<String, String> body,
      @AuthenticationPrincipal UserPrincipal p) {
    String status = body != null && body.containsKey("status") ? body.get("status") : null;
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("status is required");
    }
    adminBusinessService.setStatus(id, status.trim(), p);
    return ResponseEntity.noContent().build();
  }
}
