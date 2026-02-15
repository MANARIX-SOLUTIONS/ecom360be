package com.ecom360.store.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "store")
public class Store extends BaseEntity {
  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String address;

  private String phone;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  protected Store() {}

  public static Store create(UUID businessId, String name, String address, String phone) {
    Store s = new Store();
    s.businessId = businessId;
    s.name = name;
    s.address = address;
    s.phone = phone;
    s.isActive = true;
    return s;
  }

  public void deactivate() {
    this.isActive = false;
  }

  public void activate() {
    this.isActive = true;
  }

  public boolean belongsTo(UUID bizId) {
    return businessId.equals(bizId);
  }

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

  public String getAddress() {
    return address;
  }

  public void setAddress(String v) {
    this.address = v;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String v) {
    this.phone = v;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean v) {
    this.isActive = v;
  }
}
