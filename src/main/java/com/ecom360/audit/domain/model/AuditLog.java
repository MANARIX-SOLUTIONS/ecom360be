package com.ecom360.audit.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "business_id")
  private UUID businessId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false)
  private String action;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id")
  private UUID entityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> changes;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  public static AuditLog record(
      UUID bizId,
      UUID userId,
      String action,
      String entityType,
      UUID entityId,
      Map<String, Object> changes,
      String ip) {
    AuditLog l = new AuditLog();
    l.businessId = bizId;
    l.userId = userId;
    l.action = action;
    l.entityType = entityType;
    l.entityId = entityId;
    l.changes = changes;
    l.ipAddress = ip;
    return l;
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

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID v) {
    this.userId = v;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String v) {
    this.action = v;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String v) {
    this.entityType = v;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public void setEntityId(UUID v) {
    this.entityId = v;
  }

  public Map<String, Object> getChanges() {
    return changes;
  }

  public void setChanges(Map<String, Object> v) {
    this.changes = v;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String v) {
    this.ipAddress = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
