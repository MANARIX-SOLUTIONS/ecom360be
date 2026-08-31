package com.ecom360.sales.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sale_payment")
public class SalePayment {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "sale_id", nullable = false)
  private UUID saleId;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "store_id", nullable = false)
  private UUID storeId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /** Renseigné quand le versement provient d'un remboursement global du client. */
  @Column(name = "client_payment_id")
  private UUID clientPaymentId;

  @Column(nullable = false)
  private Integer amount;

  @Column(name = "payment_method", nullable = false)
  private String paymentMethod;

  @Column(nullable = false)
  private String kind;

  @Column(columnDefinition = "TEXT")
  private String note;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  public static SalePayment record(
      Sale sale, UUID userId, int amount, String paymentMethod, String kind, String note) {
    SalePayment p = new SalePayment();
    p.saleId = sale.getId();
    p.businessId = sale.getBusinessId();
    p.storeId = sale.getStoreId();
    p.userId = userId;
    p.amount = amount;
    p.paymentMethod = paymentMethod;
    p.kind = kind;
    p.note = note;
    return p;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID v) {
    this.id = v;
  }

  public UUID getSaleId() {
    return saleId;
  }

  public void setSaleId(UUID v) {
    this.saleId = v;
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID v) {
    this.businessId = v;
  }

  public UUID getStoreId() {
    return storeId;
  }

  public void setStoreId(UUID v) {
    this.storeId = v;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID v) {
    this.userId = v;
  }

  public UUID getClientPaymentId() {
    return clientPaymentId;
  }

  public void setClientPaymentId(UUID v) {
    this.clientPaymentId = v;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer v) {
    this.amount = v;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(String v) {
    this.paymentMethod = v;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String v) {
    this.kind = v;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String v) {
    this.note = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
