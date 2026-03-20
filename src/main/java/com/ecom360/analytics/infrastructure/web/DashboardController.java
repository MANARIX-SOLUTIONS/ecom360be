package com.ecom360.analytics.infrastructure.web;

import com.ecom360.analytics.application.dto.DashboardResponse;
import com.ecom360.analytics.application.dto.DashboardSliceResponse;
import com.ecom360.analytics.application.dto.GlobalViewResponse;
import com.ecom360.analytics.application.service.DashboardService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/dashboard")
@Tag(name = "Dashboard / Analytics")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {
  private final DashboardService svc;

  public DashboardController(DashboardService svc) {
    this.svc = svc;
  }

  @GetMapping
  @Operation(summary = "Get dashboard data (optionally scoped to one store)")
  public ResponseEntity<DashboardResponse> dashboard(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodStart,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodEnd,
      @RequestParam(required = false) UUID storeId,
      @AuthenticationPrincipal UserPrincipal p) {
    if (periodStart == null) periodStart = LocalDate.now().withDayOfMonth(1);
    if (periodEnd == null) periodEnd = LocalDate.now();
    return ResponseEntity.ok(svc.getDashboard(p, periodStart, periodEnd, storeId));
  }

  @GetMapping("/top-products")
  @Operation(summary = "Produits les plus vendus sur la période (par lots)")
  public ResponseEntity<DashboardSliceResponse<DashboardResponse.TopProduct>> topProductsSlice(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodStart,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodEnd,
      @RequestParam(required = false) UUID storeId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    if (periodStart == null) periodStart = LocalDate.now().withDayOfMonth(1);
    if (periodEnd == null) periodEnd = LocalDate.now();
    return ResponseEntity.ok(svc.sliceTopProducts(p, periodStart, periodEnd, storeId, page, size));
  }

  @GetMapping("/low-stock-items")
  @Operation(summary = "Produits en stock faible (par lots)")
  public ResponseEntity<DashboardSliceResponse<DashboardResponse.LowStockItem>> lowStockSlice(
      @RequestParam(required = false) UUID storeId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.sliceLowStockItems(p, storeId, page, size));
  }

  @GetMapping("/global")
  @Operation(summary = "Vue globale de toutes les boutiques")
  public ResponseEntity<GlobalViewResponse> globalView(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodStart,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodEnd,
      @AuthenticationPrincipal UserPrincipal p) {
    if (periodStart == null) periodStart = LocalDate.now().withDayOfMonth(1);
    if (periodEnd == null) periodEnd = LocalDate.now();
    return ResponseEntity.ok(svc.getGlobalView(p, periodStart, periodEnd));
  }
}
