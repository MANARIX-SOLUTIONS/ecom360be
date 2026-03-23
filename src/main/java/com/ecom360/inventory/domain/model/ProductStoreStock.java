package com.ecom360.inventory.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "product_store_stock",
    uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "store_id"}))
public class ProductStoreStock {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "store_id", nullable = false)
  private UUID storeId;

  @Column(nullable = false)
  private Integer quantity = 0;

  @Column(name = "min_stock", nullable = false)
  private Integer minStock = 0;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  public boolean isLowStock() {
    return quantity <= minStock;
  }

  public void adjustQuantity(int delta) {
    this.quantity += delta;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID v) {
    this.id = v;
  }

  public UUID getProductId() {
    return productId;
  }

  public void setProductId(UUID v) {
    this.productId = v;
  }

  public UUID getStoreId() {
    return storeId;
  }

  public void setStoreId(UUID v) {
    this.storeId = v;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer v) {
    this.quantity = v;
  }

  public Integer getMinStock() {
    return minStock;
  }

  public void setMinStock(Integer v) {
    this.minStock = v;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
