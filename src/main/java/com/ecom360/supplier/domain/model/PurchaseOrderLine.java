package com.ecom360.supplier.domain.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_line")
public class PurchaseOrderLine {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "purchase_order_id", nullable = false)
  private UUID purchaseOrderId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "unit_cost", nullable = false)
  private Integer unitCost;

  @Column(name = "line_total", nullable = false)
  private Integer lineTotal;

  public UUID getId() {
    return id;
  }

  public void setId(UUID v) {
    this.id = v;
  }

  public UUID getPurchaseOrderId() {
    return purchaseOrderId;
  }

  public void setPurchaseOrderId(UUID v) {
    this.purchaseOrderId = v;
  }

  public UUID getProductId() {
    return productId;
  }

  public void setProductId(UUID v) {
    this.productId = v;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer v) {
    this.quantity = v;
  }

  public Integer getUnitCost() {
    return unitCost;
  }

  public void setUnitCost(Integer v) {
    this.unitCost = v;
  }

  public Integer getLineTotal() {
    return lineTotal;
  }

  public void setLineTotal(Integer v) {
    this.lineTotal = v;
  }
}
