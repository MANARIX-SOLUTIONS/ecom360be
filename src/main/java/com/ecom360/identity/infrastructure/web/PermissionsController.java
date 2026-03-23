package com.ecom360.identity.infrastructure.web;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.domain.model.AppPermission;
import com.ecom360.tenant.domain.repository.AppPermissionRepository;
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

@RestController
@RequestMapping(ApiConstants.API_BASE + "/permissions")
@Tag(name = "Permissions", description = "User permissions by role")
@SecurityRequirement(name = "bearerAuth")
public class PermissionsController {

  private final RolePermissionService permissionService;
  private final AppPermissionRepository appPermissionRepository;

  public PermissionsController(
      RolePermissionService permissionService, AppPermissionRepository appPermissionRepository) {
    this.permissionService = permissionService;
    this.appPermissionRepository = appPermissionRepository;
  }

  @GetMapping
  @Operation(summary = "Lister toutes les permissions (catalogue)")
  public List<String> listPermissionCodes() {
    return appPermissionRepository.findAllByOrderByCodeAsc().stream()
        .map(AppPermission::getCode)
        .toList();
  }

  @GetMapping("/me")
  @Operation(summary = "Get current user permissions")
  public ResponseEntity<PermissionsResponse> getMyPermissions(
      @AuthenticationPrincipal UserPrincipal p) {
    List<String> permissions =
        Arrays.stream(Permission.values())
            .filter(perm -> permissionService.can(p, perm))
            .map(Enum::name)
            .collect(Collectors.toList());
    return ResponseEntity.ok(new PermissionsResponse(p.role(), permissions));
  }

  public record PermissionsResponse(String role, List<String> permissions) {}
}
