package com.ecom360.supplier.domain.model;

import com.ecom360.shared.domain.model.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "supplier")
public class Supplier extends AggregateRoot {
  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(nullable = false)
  private String name;

  private String phone;
  private String email;

  @Column(length = 100)
  private String zone;

  @Column(columnDefinition = "TEXT")
  private String address;

  @Column(nullable = false)
  private Integer balance = 0;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  public void addToBalance(int amount) {
    this.balance += amount;
  }

  public void deductFromBalance(int amount) {
    this.balance -= amount;
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

  public String getZone() {
    return zone;
  }

  public void setZone(String v) {
    this.zone = v;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String v) {
    this.address = v;
  }

  public Integer getBalance() {
    return balance;
  }

  public void setBalance(Integer v) {
    this.balance = v;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean v) {
    this.isActive = v;
  }
}
