package com.ecom360.sales.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.sales.application.dto.*;
import com.ecom360.sales.application.service.SaleService;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/sales")
@Tag(name = "Sales / POS")
@SecurityRequirement(name = "bearerAuth")
public class SaleController {
  private final SaleService svc;

  public SaleController(SaleService svc) {
    this.svc = svc;
  }

  @PostMapping
  @Operation(summary = "Create sale")
  public ResponseEntity<SaleResponse> create(
      @Valid @RequestBody SaleRequest r, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(svc.createSale(r, p));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get sale")
  public ResponseEntity<SaleResponse> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.getById(id, p));
  }

  @GetMapping
  @Operation(summary = "List sales")
  public ResponseEntity<PageResponse<SaleResponse>> list(
      @RequestParam(required = false) UUID storeId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodStart,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodEnd,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String paymentStatus,
      @RequestParam(required = false) UUID clientId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    ZoneId zone = ZoneId.systemDefault();
    Instant from = periodStart != null ? periodStart.atStartOfDay(zone).toInstant() : null;
    Instant to = periodEnd != null ? periodEnd.plusDays(1).atStartOfDay(zone).toInstant() : null;
    return ResponseEntity.ok(
        PageResponse.of(
            svc.list(
                p,
                storeId,
                from,
                to,
                status != null && !status.isBlank() ? status : null,
                paymentStatus != null && !paymentStatus.isBlank() ? paymentStatus : null,
                clientId,
                PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()))));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update sale (lines, discount, payment — receipt number unchanged)")
  public ResponseEntity<SaleResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody SaleRequest r,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.updateSale(id, r, p));
  }

  @PostMapping("/{id}/void")
  @Operation(summary = "Void sale")
  public ResponseEntity<SaleResponse> voidSale(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.voidSale(id, p));
  }

  @PostMapping("/{id}/payments")
  @Operation(summary = "Record a payment against the outstanding balance of a sale")
  public ResponseEntity<SalePaymentResponse> recordPayment(
      @PathVariable UUID id,
      @Valid @RequestBody SalePaymentRequest r,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(svc.recordPayment(id, r, p));
  }

  @GetMapping("/{id}/payments")
  @Operation(summary = "List payments recorded on a sale")
  public ResponseEntity<List<SalePaymentResponse>> listPayments(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.listPayments(id, p));
  }
}
