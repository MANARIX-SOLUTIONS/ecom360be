package com.ecom360.delivery.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "livraison",
    indexes = {
      @Index(name = "idx_livraison_business_id", columnList = "business_id"),
      @Index(name = "idx_livraison_courier_id", columnList = "courier_id"),
      @Index(name = "idx_livraison_delivered_at", columnList = "courier_id, delivered_at")
    })
public class Delivery extends BaseEntity {

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "courier_id", nullable = false)
  private UUID courierId;

  @Column(name = "sale_id")
  private UUID saleId;

  @Column(nullable = false, length = 20)
  private String status = "delivered"; // delivered, failed, cancelled

  @Column(name = "parcels_count", nullable = false)
  private Integer parcelsCount = 1;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(columnDefinition = "TEXT")
  private String notes;

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID v) {
    this.businessId = v;
  }

  public UUID getCourierId() {
    return courierId;
  }

  public void setCourierId(UUID v) {
    this.courierId = v;
  }

  public UUID getSaleId() {
    return saleId;
  }

  public void setSaleId(UUID v) {
    this.saleId = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    this.status = v;
  }

  public Integer getParcelsCount() {
    return parcelsCount;
  }

  public void setParcelsCount(Integer v) {
    this.parcelsCount = v;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public void setDeliveredAt(Instant v) {
    this.deliveredAt = v;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String v) {
    this.notes = v;
  }
}
