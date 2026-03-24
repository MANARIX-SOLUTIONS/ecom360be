package com.ecom360.admin.infrastructure.web;

import com.ecom360.admin.application.service.AdminStoreService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.store.application.dto.StoreRequest;
import com.ecom360.store.application.dto.StoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/admin/businesses/{businessId}/stores")
@Tag(name = "Admin Stores", description = "Plateforme : boutiques par entreprise")
@SecurityRequirement(name = "bearerAuth")
public class AdminBusinessStoreController {

  private final AdminStoreService adminStoreService;

  public AdminBusinessStoreController(AdminStoreService adminStoreService) {
    this.adminStoreService = adminStoreService;
  }

  @GetMapping
  @Operation(summary = "Lister les boutiques d'une entreprise")
  public ResponseEntity<List<StoreResponse>> list(
      @PathVariable UUID businessId, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(adminStoreService.list(businessId, p));
  }

  @PostMapping
  @Operation(summary = "Créer une boutique (respecte la limite du plan)")
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<StoreResponse> create(
      @PathVariable UUID businessId,
      @Valid @RequestBody StoreRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(HttpStatus.CREATED).body(adminStoreService.create(businessId, req, p));
  }

  @PutMapping("/{storeId}")
  @Operation(summary = "Modifier une boutique")
  public ResponseEntity<StoreResponse> update(
      @PathVariable UUID businessId,
      @PathVariable UUID storeId,
      @Valid @RequestBody StoreRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(adminStoreService.update(businessId, storeId, req, p));
  }

  @DeleteMapping("/{storeId}")
  @Operation(summary = "Supprimer une boutique")
  public ResponseEntity<Void> delete(
      @PathVariable UUID businessId,
      @PathVariable UUID storeId,
      @AuthenticationPrincipal UserPrincipal p) {
    adminStoreService.delete(businessId, storeId, p);
    return ResponseEntity.noContent().build();
  }
}
