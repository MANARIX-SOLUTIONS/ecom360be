package com.ecom360.catalog.infrastructure.web;

import com.ecom360.catalog.application.dto.EligiblePerformerResponse;
import com.ecom360.catalog.application.dto.ProductPerformerResponse;
import com.ecom360.catalog.application.dto.ReplaceProductPerformersRequest;
import com.ecom360.catalog.application.service.ProductPerformerService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE)
@Tag(name = "Product performers")
@SecurityRequirement(name = "bearerAuth")
public class ProductPerformerController {
  private final ProductPerformerService performers;

  public ProductPerformerController(ProductPerformerService performers) {
    this.performers = performers;
  }

  @GetMapping("/products/{id}/performers")
  @Operation(summary = "Employés habilités sur une prestation")
  public ResponseEntity<List<ProductPerformerResponse>> list(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(performers.list(id, principal));
  }

  @PutMapping("/products/{id}/performers")
  @Operation(summary = "Remplacer les employés habilités sur une prestation")
  public ResponseEntity<List<ProductPerformerResponse>> replace(
      @PathVariable UUID id,
      @Valid @RequestBody ReplaceProductPerformersRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(performers.replace(id, request.businessUserIds(), principal));
  }

  @GetMapping("/stores/{storeId}/products/{productId}/eligible-performers")
  @Operation(summary = "Employés proposés à la caisse pour une prestation dans ce salon")
  public ResponseEntity<List<EligiblePerformerResponse>> eligible(
      @PathVariable UUID storeId,
      @PathVariable UUID productId,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(performers.eligible(storeId, productId, principal));
  }
}
