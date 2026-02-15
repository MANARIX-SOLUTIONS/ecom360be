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

  public boolean isActive() {
    return "active".equals(status) || "trialing".equals(status);
  }

  public void cancel() {
    this.status = "cancelled";
    this.cancelledAt = LocalDate.now();
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
}
