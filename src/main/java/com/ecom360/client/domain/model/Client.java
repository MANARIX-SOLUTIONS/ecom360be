package com.ecom360.client.domain.model;

import com.ecom360.shared.domain.model.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "client")
public class Client extends AggregateRoot {

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(nullable = false)
  private String name;

  @Column(length = 50)
  private String phone;

  @Column(length = 255)
  private String email;

  @Column(columnDefinition = "TEXT")
  private String address;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(name = "credit_balance", nullable = false)
  private Integer creditBalance = 0;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  public void addCredit(int amount) {
    this.creditBalance += amount;
  }

  public void deductCredit(int amount) {
    this.creditBalance -= amount;
  }

  // Getters and setters
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

  public String getAddress() {
    return address;
  }

  public void setAddress(String v) {
    this.address = v;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String v) {
    this.notes = v;
  }

  public Integer getCreditBalance() {
    return creditBalance;
  }

  public void setCreditBalance(Integer v) {
    this.creditBalance = v;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean v) {
    this.isActive = v;
  }
}
