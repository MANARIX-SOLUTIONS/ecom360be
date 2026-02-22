package com.ecom360.tenant.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subscription")
public class Subscription extends BaseEntity {

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  @Column(name = "billing_cycle", nullable = false)
  private String billingCycle;

  @Column(nullable = false)
  private String status;

  @Column(name = "current_period_start", nullable = false)
  private LocalDate currentPeriodStart;

  @Column(name = "current_period_end", nullable = false)
  private LocalDate currentPeriodEnd;

  @Column(name = "cancelled_at")
  private LocalDate cancelledAt;

  @Column(name = "cancel_at_period_end")
  private Boolean cancelAtPeriodEnd = false;

  @Column(name = "expired_at")
  private LocalDate expiredAt;

  @Column(name = "grace_period_ends_at")
  private LocalDate gracePeriodEndsAt;

  public boolean isActive() {
    return SubscriptionStatus.ACCESS_GRANTING.contains(status);
  }

  public boolean isTrialing() {
    return SubscriptionStatus.TRIALING.equals(status);
  }

  public boolean isExpired() {
    return SubscriptionStatus.EXPIRED.equals(status);
  }

  public boolean isCancelled() {
    return SubscriptionStatus.CANCELLED.equals(status);
  }

  public boolean isPastDue() {
    return SubscriptionStatus.PAST_DUE.equals(status);
  }

  public void cancelImmediate() {
    this.status = SubscriptionStatus.CANCELLED;
    this.cancelledAt = LocalDate.now();
    this.cancelAtPeriodEnd = false;
  }

  public void cancelAtPeriodEnd() {
    this.cancelAtPeriodEnd = true;
  }

  public void expire() {
    this.status = SubscriptionStatus.EXPIRED;
    this.expiredAt = LocalDate.now();
    this.cancelAtPeriodEnd = false;
  }

  public void markCancelled() {
    this.status = SubscriptionStatus.CANCELLED;
    this.cancelledAt = LocalDate.now();
    this.cancelAtPeriodEnd = false;
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID v) {
    this.businessId = v;
  }

  public UUID getPlanId() {
    return planId;
  }

  public void setPlanId(UUID v) {
    this.planId = v;
  }

  public String getBillingCycle() {
    return billingCycle;
  }

  public void setBillingCycle(String v) {
    this.billingCycle = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    this.status = v;
  }

  public LocalDate getCurrentPeriodStart() {
    return currentPeriodStart;
  }

  public void setCurrentPeriodStart(LocalDate v) {
    this.currentPeriodStart = v;
  }

  public LocalDate getCurrentPeriodEnd() {
    return currentPeriodEnd;
  }

  public void setCurrentPeriodEnd(LocalDate v) {
    this.currentPeriodEnd = v;
  }

  public LocalDate getCancelledAt() {
    return cancelledAt;
  }

  public void setCancelledAt(LocalDate v) {
    this.cancelledAt = v;
  }

  public Boolean getCancelAtPeriodEnd() {
    return cancelAtPeriodEnd != null && cancelAtPeriodEnd;
  }

  public void setCancelAtPeriodEnd(Boolean v) {
    this.cancelAtPeriodEnd = v != null ? v : false;
  }

  public LocalDate getExpiredAt() {
    return expiredAt;
  }

  public void setExpiredAt(LocalDate v) {
    this.expiredAt = v;
  }

  public LocalDate getGracePeriodEndsAt() {
    return gracePeriodEndsAt;
  }

  public void setGracePeriodEndsAt(LocalDate v) {
    this.gracePeriodEndsAt = v;
  }
}
