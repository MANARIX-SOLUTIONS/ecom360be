package com.ecom360.delivery.infrastructure.web;

import com.ecom360.delivery.application.dto.CourierRequest;
import com.ecom360.delivery.application.dto.CourierResponse;
import com.ecom360.delivery.application.dto.CourierStatsResponse;
import com.ecom360.delivery.application.service.CourierService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
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
@RequestMapping(ApiConstants.API_BASE + "/delivery/couriers")
@Tag(name = "Delivery - Couriers (Livreurs)")
@SecurityRequirement(name = "bearerAuth")
public class CourierController {

  private final CourierService courierService;

  public CourierController(CourierService courierService) {
    this.courierService = courierService;
  }

  @GetMapping
  @Operation(summary = "List couriers (livreurs)")
  public ResponseEntity<List<CourierResponse>> list(
      @RequestParam(required = false, defaultValue = "false") boolean activeOnly,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(courierService.list(p, activeOnly));
  }

  @GetMapping("/stats")
  @Operation(summary = "Get performance stats for all couriers")
  public ResponseEntity<List<CourierStatsResponse>> getAllStats(
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(courierService.getAllStats(p));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get courier by id")
  public ResponseEntity<CourierResponse> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(courierService.getById(id, p));
  }

  @GetMapping("/{id}/stats")
  @Operation(summary = "Get performance stats for one courier")
  public ResponseEntity<CourierStatsResponse> getStats(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(courierService.getStats(id, p));
  }

  @PostMapping
  @Operation(summary = "Create courier")
  public ResponseEntity<CourierResponse> create(
      @Valid @RequestBody CourierRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(courierService.create(req, p));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update courier")
  public ResponseEntity<CourierResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody CourierRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(courierService.update(id, req, p));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete courier")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    courierService.delete(id, p);
    return ResponseEntity.noContent().build();
  }
}
