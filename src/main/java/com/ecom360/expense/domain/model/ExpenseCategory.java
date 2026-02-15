package com.ecom360.expense.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "expense_category")
public class ExpenseCategory {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(nullable = false)
  private String name;

  private String color;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID v) {
    this.id = v;
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

  public String getColor() {
    return color;
  }

  public void setColor(String v) {
    this.color = v;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(Integer v) {
    this.sortOrder = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
