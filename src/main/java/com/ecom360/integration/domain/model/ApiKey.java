package com.ecom360.integration.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "api_key", uniqueConstraints = @UniqueConstraint(columnNames = "key_hash"))
public class ApiKey {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "key_hash", nullable = false, unique = true)
  private String keyHash;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  private String permissions;

  @Column(name = "expires_at")
  private LocalDate expiresAt;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  public boolean isExpired() {
    return expiresAt != null && LocalDate.now().isAfter(expiresAt);
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID v) {
    this.id = v;
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID v) {
    this.businessId = v;
  }

  public String getKeyHash() {
    return keyHash;
  }

  public void setKeyHash(String v) {
    this.keyHash = v;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String v) {
    this.label = v;
  }

  public String getPermissions() {
    return permissions;
  }

  public void setPermissions(String v) {
    this.permissions = v;
  }

  public LocalDate getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDate v) {
    this.expiresAt = v;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean v) {
    this.isActive = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
