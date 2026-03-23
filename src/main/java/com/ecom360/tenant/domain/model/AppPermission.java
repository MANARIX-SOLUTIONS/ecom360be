package com.ecom360.tenant.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "app_permission")
public class AppPermission extends BaseEntity {

  @Column(nullable = false, unique = true, length = 100)
  private String code;

  protected AppPermission() {}

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }
}
