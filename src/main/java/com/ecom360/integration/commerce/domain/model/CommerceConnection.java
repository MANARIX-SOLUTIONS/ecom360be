package com.ecom360.integration.commerce.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "commerce_connection")
public class CommerceConnection extends BaseEntity {

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "store_id", nullable = false)
  private UUID storeId;

  @Column(name = "source_type", nullable = false, length = 64)
  private String sourceType;

  @Column(nullable = false)
  private String label;

  @Column(name = "incoming_token", nullable = false, unique = true, length = 64)
  private String incomingToken;

  /** Secret partagé pour HMAC-SHA256 du corps ; transmis une seule fois à la création. */
  @Column(name = "hmac_secret", nullable = false, length = 128)
  private String hmacSecret;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID businessId) {
    this.businessId = businessId;
  }

  public UUID getStoreId() {
    return storeId;
  }

  public void setStoreId(UUID storeId) {
    this.storeId = storeId;
  }

  public String getSourceType() {
    return sourceType;
  }

  public void setSourceType(String sourceType) {
    this.sourceType = sourceType;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getIncomingToken() {
    return incomingToken;
  }

  public void setIncomingToken(String incomingToken) {
    this.incomingToken = incomingToken;
  }

  public String getHmacSecret() {
    return hmacSecret;
  }

  public void setHmacSecret(String hmacSecret) {
    this.hmacSecret = hmacSecret;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }
}
