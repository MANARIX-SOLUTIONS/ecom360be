package com.ecom360.identity.infrastructure.web;

import com.ecom360.identity.application.dto.PermissionsResponse;
import com.ecom360.identity.application.service.PermissionsResponseBuilder;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Alias REST de {@code GET /permissions/me} pour la spec {@code GET /me/permissions}. */
@RestController
@RequestMapping(ApiConstants.API_BASE)
@Tag(name = "Permissions")
@SecurityRequirement(name = "bearerAuth")
public class MePermissionsController {

  private final PermissionsResponseBuilder permissionsResponseBuilder;

  public MePermissionsController(PermissionsResponseBuilder permissionsResponseBuilder) {
    this.permissionsResponseBuilder = permissionsResponseBuilder;
  }

  @GetMapping("/me/permissions")
  @Operation(summary = "Rôles et permissions de l'utilisateur courant (alias)")
  public ResponseEntity<PermissionsResponse> getMyPermissions(
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(permissionsResponseBuilder.build(p));
  }
}
