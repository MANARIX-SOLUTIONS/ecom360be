package com.ecom360.identity.infrastructure.web;

import com.ecom360.identity.application.dto.PermissionCatalogItem;
import com.ecom360.identity.application.dto.PermissionsResponse;
import com.ecom360.identity.application.service.PermissionsResponseBuilder;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.domain.repository.AppPermissionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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

  private final PermissionsResponseBuilder permissionsResponseBuilder;
  private final AppPermissionRepository appPermissionRepository;

  public PermissionsController(
      PermissionsResponseBuilder permissionsResponseBuilder,
      AppPermissionRepository appPermissionRepository) {
    this.permissionsResponseBuilder = permissionsResponseBuilder;
    this.appPermissionRepository = appPermissionRepository;
  }

  @GetMapping
  @Operation(summary = "Catalogue des permissions (codes, libellés, regroupement)")
  public List<PermissionCatalogItem> listPermissionCatalog() {
    return appPermissionRepository.findAllByOrderBySortOrderAscCodeAsc().stream()
        .map(
            ap ->
                new PermissionCatalogItem(
                    ap.getCode(),
                    ap.getLabel(),
                    ap.getCategory() != null ? ap.getCategory() : "other",
                    ap.getSortOrder()))
        .toList();
  }

  @GetMapping("/me")
  @Operation(summary = "Permissions effectives et matrice navigation")
  public ResponseEntity<PermissionsResponse> getMyPermissions(
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(permissionsResponseBuilder.build(p));
  }
}
