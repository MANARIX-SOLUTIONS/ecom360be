package com.ecom360.supplier.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.supplier.application.dto.*;
import com.ecom360.supplier.application.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/suppliers")
@Tag(name = "Suppliers")
@SecurityRequirement(name = "bearerAuth")
public class SupplierController {
  private final SupplierService svc;

  public SupplierController(SupplierService svc) {
    this.svc = svc;
  }

  @PostMapping
  @Operation(summary = "Create supplier")
  public ResponseEntity<SupplierResponse> create(
      @Valid @RequestBody SupplierRequest r, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(svc.create(r, p));
  }

  @GetMapping
  @Operation(summary = "List suppliers")
  public ResponseEntity<PageResponse<SupplierResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(
            svc.list(
                p, PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()))));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get supplier")
  public ResponseEntity<SupplierResponse> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.getById(id, p));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update supplier")
  public ResponseEntity<SupplierResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody SupplierRequest r,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.update(id, r, p));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete supplier")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    svc.delete(id, p);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/payments")
  @Operation(summary = "Record supplier payment")
  public ResponseEntity<SupplierPaymentResponse> pay(
      @PathVariable UUID id,
      @Valid @RequestBody SupplierPaymentRequest r,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(svc.recordPayment(id, r, p));
  }

  @GetMapping("/{id}/payments")
  @Operation(summary = "List supplier payments")
  public ResponseEntity<PageResponse<SupplierPaymentResponse>> payments(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(svc.getPayments(id, p, PageRequest.of(page, Math.min(size, 100)))));
  }
}
