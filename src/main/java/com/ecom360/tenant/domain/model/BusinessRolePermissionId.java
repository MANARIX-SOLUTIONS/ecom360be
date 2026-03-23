package com.ecom360.tenant.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class BusinessRolePermissionId implements Serializable {

  @Column(name = "role_id")
  private UUID roleId;

  @Column(name = "permission_id")
  private UUID permissionId;

  protected BusinessRolePermissionId() {}

  public BusinessRolePermissionId(UUID roleId, UUID permissionId) {
    this.roleId = roleId;
    this.permissionId = permissionId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public UUID getPermissionId() {
    return permissionId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BusinessRolePermissionId that = (BusinessRolePermissionId) o;
    return Objects.equals(roleId, that.roleId) && Objects.equals(permissionId, that.permissionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(roleId, permissionId);
  }
}
