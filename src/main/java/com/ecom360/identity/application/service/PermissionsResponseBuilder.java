package com.ecom360.identity.application.service;

import com.ecom360.identity.application.NavigationPermissionRules;
import com.ecom360.identity.application.dto.PermissionsResponse;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PermissionsResponseBuilder {

  private final RolePermissionService permissionService;

  public PermissionsResponseBuilder(RolePermissionService permissionService) {
    this.permissionService = permissionService;
  }

  public PermissionsResponse build(UserPrincipal p) {
    List<String> permissions =
        Arrays.stream(Permission.values())
            .filter(perm -> permissionService.can(p, perm))
            .map(Enum::name)
            .collect(Collectors.toList());
    return new PermissionsResponse(p.role(), permissions, NavigationPermissionRules.asMap());
  }
}
