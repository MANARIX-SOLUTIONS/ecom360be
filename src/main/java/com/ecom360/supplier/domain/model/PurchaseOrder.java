package com.ecom360.supplier.domain.model;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "purchase_order", uniqueConstraints = @UniqueConstraint(columnNames = "reference"))
public class PurchaseOrder extends BaseEntity {
  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "supplier_id", nullable = false)
  private UUID supplierId;

  @Column(name = "store_id", nullable = false)
  private UUID storeId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, unique = true)
  private String reference;

  @Column(nullable = false)
  private String status;

  @Column(name = "total_amount", nullable = false)
  private Integer totalAmount;

  @Column(name = "amount_paid", nullable = false)
  private Integer amountPaid = 0;

  @Column(name = "payment_status", nullable = false)
  private String paymentStatus = PurchaseOrderPaymentStatus.UNPAID;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "expected_date")
  private LocalDate expectedDate;

  @Column(name = "received_date")
  private LocalDate receivedDate;

  @Column(columnDefinition = "TEXT")
  private String note;

  public void transitionTo(String next) {
    switch (status) {
      case "draft" -> {
        if (!"ordered".equals(next) && !"cancelled".equals(next))
          throw new BusinessRuleException("Draft->ordered|cancelled only");
      }
      case "ordered" -> {
        if (!"received".equals(next) && !"cancelled".equals(next))
          throw new BusinessRuleException("Ordered->received|cancelled only");
      }
      default -> throw new BusinessRuleException("Cannot change status of " + status + " order");
    }
    this.status = next;
    if ("received".equals(next)) this.receivedDate = LocalDate.now();
  }

  public boolean isReceived() {
    return "received".equals(status);
  }

  /** Solde restant à régler au fournisseur sur ce bon. */
  public int getRemainingAmount() {
    int due = totalAmount == null ? 0 : totalAmount;
    int paid = amountPaid == null ? 0 : amountPaid;
    return Math.max(0, due - paid);
  }

  public boolean hasOutstandingBalance() {
    return isReceived() && getRemainingAmount() > 0;
  }

  /** Impute un versement et recalcule le statut de paiement. */
  public void applyPayment(int amount) {
    this.amountPaid = (amountPaid == null ? 0 : amountPaid) + amount;
    recomputePaymentStatus();
  }

  public void recomputePaymentStatus() {
    int due = totalAmount == null ? 0 : totalAmount;
    int paid = amountPaid == null ? 0 : amountPaid;
    if (paid >= due) {
      this.paymentStatus = PurchaseOrderPaymentStatus.PAID;
    } else if (paid > 0) {
      this.paymentStatus = PurchaseOrderPaymentStatus.PARTIAL;
    } else {
      this.paymentStatus = PurchaseOrderPaymentStatus.UNPAID;
    }
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID v) {
    this.businessId = v;
  }

  public UUID getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(UUID v) {
    this.supplierId = v;
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

  public String getReference() {
    return reference;
  }

  public void setReference(String v) {
    this.reference = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    this.status = v;
  }

  public Integer getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(Integer v) {
    this.totalAmount = v;
  }

  public Integer getAmountPaid() {
    return amountPaid;
  }

  public void setAmountPaid(Integer v) {
    this.amountPaid = v;
  }

  public String getPaymentStatus() {
    return paymentStatus;
  }

  public void setPaymentStatus(String v) {
    this.paymentStatus = v;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDate v) {
    this.dueDate = v;
  }

  public LocalDate getExpectedDate() {
    return expectedDate;
  }

  public void setExpectedDate(LocalDate v) {
    this.expectedDate = v;
  }

  public LocalDate getReceivedDate() {
    return receivedDate;
  }

  public void setReceivedDate(LocalDate v) {
    this.receivedDate = v;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String v) {
    this.note = v;
  }
}
