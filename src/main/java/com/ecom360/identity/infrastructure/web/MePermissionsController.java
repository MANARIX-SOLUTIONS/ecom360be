package com.ecom360.identity.infrastructure.web;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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

  private final RolePermissionService permissionService;

  public MePermissionsController(RolePermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @GetMapping("/me/permissions")
  @Operation(summary = "Rôles et permissions de l'utilisateur courant (alias)")
  public ResponseEntity<PermissionsController.PermissionsResponse> getMyPermissions(
      @AuthenticationPrincipal UserPrincipal p) {
    List<String> permissions =
        Arrays.stream(Permission.values())
            .filter(perm -> permissionService.can(p, perm))
            .map(Enum::name)
            .collect(Collectors.toList());
    return ResponseEntity.ok(new PermissionsController.PermissionsResponse(p.role(), permissions));
  }
}
