package com.ecom360.supplier.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.supplier.application.dto.*;
import com.ecom360.supplier.application.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/purchase-orders")
@Tag(name = "Purchase Orders")
@SecurityRequirement(name = "bearerAuth")
public class PurchaseOrderController {
  private final PurchaseOrderService svc;

  public PurchaseOrderController(PurchaseOrderService svc) {
    this.svc = svc;
  }

  @PostMapping
  @Operation(summary = "Create purchase order")
  public ResponseEntity<PurchaseOrderResponse> create(
      @Valid @RequestBody PurchaseOrderRequest r, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(svc.create(r, p));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get purchase order")
  public ResponseEntity<PurchaseOrderResponse> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.getById(id, p));
  }

  @GetMapping
  @Operation(summary = "List purchase orders")
  public ResponseEntity<PageResponse<PurchaseOrderResponse>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID supplierId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(
            svc.list(p, status, supplierId, PageRequest.of(page, Math.min(size, 100)))));
  }

  @PatchMapping("/{id}/status")
  @Operation(summary = "Update purchase order status")
  public ResponseEntity<PurchaseOrderResponse> updateStatus(
      @PathVariable UUID id,
      @RequestBody Map<String, String> body,
      @AuthenticationPrincipal UserPrincipal p) {
    String status = body != null ? body.get("status") : null;
    if (status == null || status.isBlank())
      throw new IllegalArgumentException("status is required");
    return ResponseEntity.ok(svc.updateStatus(id, status, p));
  }
}
