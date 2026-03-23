package com.ecom360.tenant.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.application.dto.AssignRolePermissionsRequest;
import com.ecom360.tenant.application.dto.BusinessRoleResponse;
import com.ecom360.tenant.application.dto.CreateBusinessRoleRequest;
import com.ecom360.tenant.application.service.RoleManagementService;
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
@RequestMapping(ApiConstants.API_BASE + "/roles")
@Tag(name = "Roles", description = "Rôles et permissions par entreprise")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

  private final RoleManagementService roleManagementService;

  public RoleController(RoleManagementService roleManagementService) {
    this.roleManagementService = roleManagementService;
  }

  @GetMapping
  @Operation(summary = "Lister les rôles du tenant")
  public ResponseEntity<List<BusinessRoleResponse>> list(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(roleManagementService.list(p));
  }

  @PostMapping
  @Operation(summary = "Créer un rôle personnalisé")
  public ResponseEntity<BusinessRoleResponse> create(
      @Valid @RequestBody CreateBusinessRoleRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(roleManagementService.create(req, p));
  }

  @PostMapping("/{id}/permissions")
  @Operation(summary = "Remplacer les permissions d'un rôle")
  public ResponseEntity<BusinessRoleResponse> assignPermissions(
      @PathVariable UUID id,
      @Valid @RequestBody AssignRolePermissionsRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(roleManagementService.assignPermissions(id, req, p));
  }
}
