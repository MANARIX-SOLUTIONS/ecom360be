package com.ecom360.supplier.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_payment")
public class PurchaseOrderPayment {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "purchase_order_id", nullable = false)
  private UUID purchaseOrderId;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "store_id", nullable = false)
  private UUID storeId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /** Renseigné quand le versement provient d'un paiement global du fournisseur. */
  @Column(name = "supplier_payment_id")
  private UUID supplierPaymentId;

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

  public static PurchaseOrderPayment record(
      PurchaseOrder po, UUID userId, int amount, String paymentMethod, String kind, String note) {
    PurchaseOrderPayment p = new PurchaseOrderPayment();
    p.purchaseOrderId = po.getId();
    p.businessId = po.getBusinessId();
    p.storeId = po.getStoreId();
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

  public UUID getPurchaseOrderId() {
    return purchaseOrderId;
  }

  public void setPurchaseOrderId(UUID v) {
    this.purchaseOrderId = v;
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

  public UUID getSupplierPaymentId() {
    return supplierPaymentId;
  }

  public void setSupplierPaymentId(UUID v) {
    this.supplierPaymentId = v;
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
