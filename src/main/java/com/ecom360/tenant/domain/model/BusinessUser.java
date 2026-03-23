package com.ecom360.tenant.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "business_user",
    uniqueConstraints = @UniqueConstraint(columnNames = {"business_id", "user_id"}))
public class BusinessUser extends BaseEntity {

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "role_id", nullable = false)
  private BusinessRole businessRole;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  @Column(name = "invited_at", nullable = false)
  private Instant invitedAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  protected BusinessUser() {}

  public static BusinessUser create(UUID businessId, UUID userId, BusinessRole businessRole) {
    BusinessUser bu = new BusinessUser();
    bu.businessId = businessId;
    bu.userId = userId;
    bu.businessRole = businessRole;
    bu.isActive = true;
    bu.invitedAt = Instant.now();
    bu.acceptedAt = Instant.now();
    return bu;
  }

  public boolean isAccepted() {
    return acceptedAt != null;
  }

  public boolean isActive() {
    return Boolean.TRUE.equals(isActive);
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID businessId) {
    this.businessId = businessId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public BusinessRole getBusinessRole() {
    return businessRole;
  }

  public void setBusinessRole(BusinessRole businessRole) {
    this.businessRole = businessRole;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }

  public Instant getInvitedAt() {
    return invitedAt;
  }

  public void setInvitedAt(Instant invitedAt) {
    this.invitedAt = invitedAt;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public void setAcceptedAt(Instant acceptedAt) {
    this.acceptedAt = acceptedAt;
  }
}
