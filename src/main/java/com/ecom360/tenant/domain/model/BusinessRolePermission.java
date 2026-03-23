package com.ecom360.tenant.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "business_role_permission")
public class BusinessRolePermission {

  @EmbeddedId private BusinessRolePermissionId id;

  @MapsId("roleId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "role_id")
  private BusinessRole role;

  @MapsId("permissionId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "permission_id")
  private AppPermission permission;

  protected BusinessRolePermission() {}

  public static BusinessRolePermission link(BusinessRole role, AppPermission permission) {
    BusinessRolePermission brp = new BusinessRolePermission();
    brp.id = new BusinessRolePermissionId(role.getId(), permission.getId());
    brp.role = role;
    brp.permission = permission;
    return brp;
  }

  public BusinessRolePermissionId getId() {
    return id;
  }

  public BusinessRole getRole() {
    return role;
  }

  public AppPermission getPermission() {
    return permission;
  }
}
