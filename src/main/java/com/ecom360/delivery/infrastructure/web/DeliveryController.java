package com.ecom360.delivery.infrastructure.web;

import com.ecom360.delivery.application.dto.DeliveryRequest;
import com.ecom360.delivery.application.dto.DeliveryResponse;
import com.ecom360.delivery.application.service.DeliveryService;
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
@RequestMapping(ApiConstants.API_BASE + "/delivery/deliveries")
@Tag(name = "Delivery - Livraisons")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryController {

  private final DeliveryService deliveryService;

  public DeliveryController(DeliveryService deliveryService) {
    this.deliveryService = deliveryService;
  }

  @PostMapping
  @Operation(summary = "Record a delivery (livraison)")
  public ResponseEntity<DeliveryResponse> create(
      @Valid @RequestBody DeliveryRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(deliveryService.create(req, p));
  }

  @GetMapping
  @Operation(summary = "List deliveries, optionally by courier")
  public ResponseEntity<List<DeliveryResponse>> list(
      @RequestParam(required = false) UUID courierId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    if (courierId != null) {
      return ResponseEntity.ok(
          deliveryService.listByCourier(courierId, p, page, size));
    }
    return ResponseEntity.ok(deliveryService.listByBusiness(p, page, size));
  }
}
