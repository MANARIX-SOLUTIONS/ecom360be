package com.ecom360.integration.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "webhook")
public class Webhook extends BaseEntity {
  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(nullable = false)
  private String url;

  @Column(nullable = false)
  private String events;

  @Column(name = "secret_hash", nullable = false)
  private String secretHash;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID v) {
    this.businessId = v;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String v) {
    this.url = v;
  }

  public String getEvents() {
    return events;
  }

  public void setEvents(String v) {
    this.events = v;
  }

  public String getSecretHash() {
    return secretHash;
  }

  public void setSecretHash(String v) {
    this.secretHash = v;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean v) {
    this.isActive = v;
  }
}
