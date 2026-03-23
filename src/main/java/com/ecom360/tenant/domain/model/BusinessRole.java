package com.ecom360.tenant.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "business_role",
    uniqueConstraints = @UniqueConstraint(columnNames = {"business_id", "code"}))
public class BusinessRole extends BaseEntity {

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "is_system", nullable = false)
  private boolean system;

  protected BusinessRole() {}

  public static BusinessRole createSystem(
      UUID businessId, String code, String name, boolean system) {
    BusinessRole r = new BusinessRole();
    r.businessId = businessId;
    r.code = code;
    r.name = name;
    r.system = system;
    return r;
  }

  public static BusinessRole createCustom(UUID businessId, String code, String name) {
    BusinessRole r = new BusinessRole();
    r.businessId = businessId;
    r.code = code;
    r.name = name;
    r.system = false;
    return r;
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID businessId) {
    this.businessId = businessId;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isSystem() {
    return system;
  }

  public void setSystem(boolean system) {
    this.system = system;
  }
}
