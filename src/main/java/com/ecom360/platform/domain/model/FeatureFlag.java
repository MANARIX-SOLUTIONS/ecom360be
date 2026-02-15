package com.ecom360.platform.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_flag", uniqueConstraints = @UniqueConstraint(columnNames = "key"))
public class FeatureFlag {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String key;

  @Column(nullable = false)
  private String label;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "is_enabled", nullable = false)
  private Boolean isEnabled = true;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  public void enable() {
    this.isEnabled = true;
  }

  public void disable() {
    this.isEnabled = false;
  }

  public boolean isEnabled() {
    return Boolean.TRUE.equals(isEnabled);
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID v) {
    this.id = v;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String v) {
    this.key = v;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String v) {
    this.label = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    this.description = v;
  }

  public Boolean getIsEnabled() {
    return isEnabled;
  }

  public void setIsEnabled(Boolean v) {
    this.isEnabled = v;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
