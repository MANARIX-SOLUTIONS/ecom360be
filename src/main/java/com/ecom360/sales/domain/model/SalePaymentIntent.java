package com.ecom360.sales.domain.model;

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

/** POS Wave / Orange Money payment initiated on the business's Bictorys account. */
@Entity
@Table(name = "sale_payment_intent")
public class SalePaymentIntent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "store_id", nullable = false)
  private UUID storeId;

  @Column(name = "sale_id", nullable = false)
  private UUID saleId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private Integer amount;

  @Column(nullable = false)
  private String currency = "XOF";

  @Column(nullable = false)
  private String provider = "bictorys";

  @Column(nullable = false)
  private String channel;

  @Column(nullable = false)
  private String status = SalePaymentIntentStatus.PENDING;

  @Column(name = "external_token")
  private String externalToken;

  @Column(name = "checkout_url", length = 1000)
  private String checkoutUrl;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Column(columnDefinition = "TEXT")
  private String metadata;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

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
    return SalePaymentIntentStatus.PENDING.equals(status);
  }

  /** Paid through the PSP or confirmed by hand: the sale is completed. */
  public boolean isSettled() {
    return SalePaymentIntentStatus.PAID.equals(status)
        || SalePaymentIntentStatus.MANUAL.equals(status);
  }

  public void markPaid() {
    this.status = SalePaymentIntentStatus.PAID;
    this.paidAt = Instant.now();
    this.failureReason = null;
  }

  public void markManual(String reason) {
    this.status = SalePaymentIntentStatus.MANUAL;
    this.paidAt = Instant.now();
    this.failureReason = reason;
  }

  public void markClosed(String status, String reason) {
    this.status = status;
    this.failureReason = reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500);
  }

  public UUID getId() {
    return id;
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

  public UUID getSaleId() {
    return saleId;
  }

  public void setSaleId(UUID v) {
    this.saleId = v;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID v) {
    this.userId = v;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer v) {
    this.amount = v;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String v) {
    this.currency = v;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String v) {
    this.provider = v;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String v) {
    this.channel = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    this.status = v;
  }

  public String getExternalToken() {
    return externalToken;
  }

  public void setExternalToken(String v) {
    this.externalToken = v;
  }

  public String getCheckoutUrl() {
    return checkoutUrl;
  }

  public void setCheckoutUrl(String v) {
    this.checkoutUrl = v;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(String v) {
    this.metadata = v;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant v) {
    this.expiresAt = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
