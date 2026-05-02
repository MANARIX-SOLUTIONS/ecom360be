package com.ecom360.tenant.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transaction", uniqueConstraints = @UniqueConstraint(columnNames = { "provider",
    "provider_reference" }))
public class PaymentTransaction extends BaseEntity {

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "subscription_id", nullable = false)
  private UUID subscriptionId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(nullable = false)
  private String provider;

  @Column(name = "provider_reference", nullable = false)
  private String providerReference;

  @Column(nullable = false)
  private Integer amount;

  @Column(nullable = false)
  private String currency;

  @Column(nullable = false)
  private String status = PaymentTransactionStatus.PENDING;

  @Column(name = "plan_slug", nullable = false)
  private String planSlug;

  @Column(name = "billing_cycle", nullable = false)
  private String billingCycle;

  @Column(name = "checkout_url", length = 1000)
  private String checkoutUrl;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "raw_callback", columnDefinition = "TEXT")
  private String rawCallback;

  public boolean isFinalized() {
    return PaymentTransactionStatus.PAID.equals(status)
        || PaymentTransactionStatus.FAILED.equals(status)
        || PaymentTransactionStatus.CANCELLED.equals(status)
        || PaymentTransactionStatus.EXPIRED.equals(status);
  }

  public void markPaid(String rawCallback) {
    this.status = PaymentTransactionStatus.PAID;
    this.paidAt = Instant.now();
    this.rawCallback = rawCallback;
  }

  public void markFailed(String status, String failureReason, String rawCallback) {
    this.status = status;
    this.failureReason = failureReason;
    this.rawCallback = rawCallback;
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID businessId) {
    this.businessId = businessId;
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

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getProviderReference() {
    return providerReference;
  }

  public void setProviderReference(String providerReference) {
    this.providerReference = providerReference;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getPlanSlug() {
    return planSlug;
  }

  public void setPlanSlug(String planSlug) {
    this.planSlug = planSlug;
  }

  public String getBillingCycle() {
    return billingCycle;
  }

  public void setBillingCycle(String billingCycle) {
    this.billingCycle = billingCycle;
  }

  public String getCheckoutUrl() {
    return checkoutUrl;
  }

  public void setCheckoutUrl(String checkoutUrl) {
    this.checkoutUrl = checkoutUrl;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(Instant paidAt) {
    this.paidAt = paidAt;
  }

  public String getRawCallback() {
    return rawCallback;
  }

  public void setRawCallback(String rawCallback) {
    this.rawCallback = rawCallback;
  }
}
