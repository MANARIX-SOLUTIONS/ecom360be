package com.ecom360.sales.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.sales.application.dto.DigitalCheckoutAvailabilityResponse;
import com.ecom360.sales.application.dto.DigitalCheckoutRequest;
import com.ecom360.sales.application.dto.DigitalCheckoutResponse;
import com.ecom360.sales.application.service.PosDigitalCheckoutService;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/sales/digital-checkout")
@Tag(name = "Sales / POS")
@SecurityRequirement(name = "bearerAuth")
public class PosDigitalCheckoutController {

  private final PosDigitalCheckoutService service;

  public PosDigitalCheckoutController(PosDigitalCheckoutService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(summary = "Start a Wave / Orange Money POS payment via Bictorys (plan Business)")
  public ResponseEntity<DigitalCheckoutResponse> start(
      @Valid @RequestBody DigitalCheckoutRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(service.start(req, p));
  }

  @GetMapping("/availability")
  @Operation(summary = "Whether verified Wave / Orange Money payment is usable at the POS")
  public ResponseEntity<DigitalCheckoutAvailabilityResponse> availability(
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(service.availability(p));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get POS payment status (syncs with Bictorys while pending)")
  public ResponseEntity<DigitalCheckoutResponse> status(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(service.getStatus(id, p));
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "Cancel a pending POS payment and release the stock")
  public ResponseEntity<DigitalCheckoutResponse> cancel(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(service.cancel(id, p));
  }

  @PostMapping("/{id}/confirm-manual")
  @Operation(summary = "Confirm a pending POS payment by hand (Bictorys unavailable)")
  public ResponseEntity<DigitalCheckoutResponse> confirmManual(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(service.confirmManual(id, p));
  }
}
