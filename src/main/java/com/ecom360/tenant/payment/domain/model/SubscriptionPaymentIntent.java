package com.ecom360.tenant.payment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_payment_intent")
public class SubscriptionPaymentIntent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  @Column(name = "billing_cycle", nullable = false)
  private String billingCycle;

  @Column(nullable = false)
  private Integer amount;

  @Column(nullable = false)
  private String currency = "XOF";

  @Column(nullable = false)
  private String provider = "paydunya";

  @Column(name = "preferred_channel", nullable = false)
  private String preferredChannel;

  @Column(nullable = false)
  private String status;

  @Column(name = "external_token")
  private String externalToken;

  @Column(name = "external_ref")
  private String externalRef;

  @Column(name = "checkout_url", length = 1000)
  private String checkoutUrl;

  @Column(name = "return_url", length = 1000)
  private String returnUrl;

  @Column(name = "subscription_id")
  private UUID subscriptionId;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Column(columnDefinition = "TEXT")
  private String metadata;

  @Column(name = "created_by_user_id")
  private UUID createdByUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  public boolean isPending() {
    return SubscriptionPaymentStatus.PENDING.equals(status);
  }

  public boolean isPaid() {
    return SubscriptionPaymentStatus.PAID.equals(status);
  }

  public void markPaid() {
    this.status = SubscriptionPaymentStatus.PAID;
    this.paidAt = Instant.now();
    this.failureReason = null;
  }

  public void markFailed(String reason) {
    this.status = SubscriptionPaymentStatus.FAILED;
    this.failureReason = reason;
  }

  public void markCancelled(String reason) {
    this.status = SubscriptionPaymentStatus.CANCELLED;
    this.failureReason = reason;
  }

  public void markExpired(String reason) {
    this.status = SubscriptionPaymentStatus.EXPIRED;
    this.failureReason = reason;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID businessId) {
    this.businessId = businessId;
  }

  public UUID getPlanId() {
    return planId;
  }

  public void setPlanId(UUID planId) {
    this.planId = planId;
  }

  public String getBillingCycle() {
    return billingCycle;
  }

  public void setBillingCycle(String billingCycle) {
    this.billingCycle = billingCycle;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getPreferredChannel() {
    return preferredChannel;
  }

  public void setPreferredChannel(String preferredChannel) {
    this.preferredChannel = preferredChannel;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getExternalToken() {
    return externalToken;
  }

  public void setExternalToken(String externalToken) {
    this.externalToken = externalToken;
  }

  public String getExternalRef() {
    return externalRef;
  }

  public void setExternalRef(String externalRef) {
    this.externalRef = externalRef;
  }

  public String getCheckoutUrl() {
    return checkoutUrl;
  }

  public void setCheckoutUrl(String checkoutUrl) {
    this.checkoutUrl = checkoutUrl;
  }

  public String getReturnUrl() {
    return returnUrl;
  }

  public void setReturnUrl(String returnUrl) {
    this.returnUrl = returnUrl;
  }

  public UUID getSubscriptionId() {
    return subscriptionId;
  }

  public void setSubscriptionId(UUID subscriptionId) {
    this.subscriptionId = subscriptionId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public void setInvoiceId(UUID invoiceId) {
    this.invoiceId = invoiceId;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(Instant paidAt) {
    this.paidAt = paidAt;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(String metadata) {
    this.metadata = metadata;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public void setCreatedByUserId(UUID createdByUserId) {
    this.createdByUserId = createdByUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
