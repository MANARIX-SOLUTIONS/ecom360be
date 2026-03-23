package com.ecom360.delivery.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "livreur",
    indexes = {
      @Index(name = "idx_livreur_business_id", columnList = "business_id"),
      @Index(name = "idx_livreur_active", columnList = "business_id, is_active")
    })
public class Courier extends BaseEntity {

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(length = 50)
  private String phone;

  @Column(length = 255)
  private String email;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID v) {
    this.businessId = v;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    this.name = v;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String v) {
    this.phone = v;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String v) {
    this.email = v;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean v) {
    this.isActive = v;
  }
}
