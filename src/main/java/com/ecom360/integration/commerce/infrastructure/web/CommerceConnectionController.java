package com.ecom360.integration.commerce.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.integration.commerce.application.dto.CommerceConnectionCreateRequest;
import com.ecom360.integration.commerce.application.dto.CommerceConnectionCreateResponse;
import com.ecom360.integration.commerce.application.dto.CommerceConnectionResponse;
import com.ecom360.integration.commerce.application.service.CommerceConnectionService;
import com.ecom360.shared.infrastructure.web.ApiConstants;
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
@RequestMapping(ApiConstants.API_BASE + "/commerce/connections")
@Tag(
    name = "Commerce connections",
    description = "Connexions de boutiques en ligne (webhooks entrants, modèle canonique)")
@SecurityRequirement(name = "bearerAuth")
public class CommerceConnectionController {

  private final CommerceConnectionService commerceConnectionService;

  public CommerceConnectionController(CommerceConnectionService commerceConnectionService) {
    this.commerceConnectionService = commerceConnectionService;
  }

  @PostMapping
  @Operation(summary = "Créer une connexion commerce (URL + secret HMAC affiché une seule fois)")
  public ResponseEntity<CommerceConnectionCreateResponse> create(
      @Valid @RequestBody CommerceConnectionCreateRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(commerceConnectionService.create(request, principal));
  }

  @GetMapping
  @Operation(summary = "Lister les connexions commerce")
  public ResponseEntity<List<CommerceConnectionResponse>> list(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(commerceConnectionService.list(principal));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Détail d'une connexion")
  public ResponseEntity<CommerceConnectionResponse> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(commerceConnectionService.getById(id, principal));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Supprimer une connexion")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
    commerceConnectionService.delete(id, principal);
    return ResponseEntity.noContent().build();
  }
}
