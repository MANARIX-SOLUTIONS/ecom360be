package com.ecom360.tenant.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoice", uniqueConstraints = @UniqueConstraint(columnNames = "number"))
public class Invoice {

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "business_id", nullable = false) private UUID businessId;
    @Column(name = "subscription_id", nullable = false) private UUID subscriptionId;
    @Column(nullable = false, unique = true) private String number;
    @Column(nullable = false) private Integer amount;
    @Column(nullable = false) private String status;
    @Column(name = "payment_method") private String paymentMethod;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "paid_at") private LocalDate paidAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @PrePersist protected void onCreate() { createdAt = Instant.now(); }

    public void markPaid(String paymentMethod) { this.status = "paid"; this.paidAt = LocalDate.now(); this.paymentMethod = paymentMethod; }
    public boolean isOverdue() { return "draft".equals(status) && LocalDate.now().isAfter(dueDate); }

    public UUID getId() { return id; }  public void setId(UUID v) { this.id = v; }
    public UUID getBusinessId() { return businessId; }  public void setBusinessId(UUID v) { this.businessId = v; }
    public UUID getSubscriptionId() { return subscriptionId; }  public void setSubscriptionId(UUID v) { this.subscriptionId = v; }
    public String getNumber() { return number; }  public void setNumber(String v) { this.number = v; }
    public Integer getAmount() { return amount; }  public void setAmount(Integer v) { this.amount = v; }
    public String getStatus() { return status; }  public void setStatus(String v) { this.status = v; }
    public String getPaymentMethod() { return paymentMethod; }  public void setPaymentMethod(String v) { this.paymentMethod = v; }
    public LocalDate getDueDate() { return dueDate; }  public void setDueDate(LocalDate v) { this.dueDate = v; }
    public LocalDate getPaidAt() { return paidAt; }  public void setPaidAt(LocalDate v) { this.paidAt = v; }
    public Instant getCreatedAt() { return createdAt; }
}
