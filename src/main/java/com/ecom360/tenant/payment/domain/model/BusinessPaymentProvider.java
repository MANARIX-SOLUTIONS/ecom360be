package com.ecom360.tenant.payment.domain.model;

import com.ecom360.shared.infrastructure.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A business's own PSP merchant account (POS payments land on it directly). */
@Entity
@Table(name = "business_payment_provider")
public class BusinessPaymentProvider {

  public static final String BICTORYS = "bictorys";
  public static final String ENV_TEST = "test";
  public static final String ENV_LIVE = "live";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(nullable = false)
  private String provider = BICTORYS;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "api_key_enc", nullable = false, columnDefinition = "TEXT")
  private String apiKey;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "webhook_secret_enc", nullable = false, columnDefinition = "TEXT")
  private String webhookSecret;

  @Column(nullable = false)
  private String country = "SN";

  @Column(nullable = false)
  private String environment = ENV_TEST;

  @Column(name = "webhook_token", nullable = false, unique = true)
  private String webhookToken;

  @Column(nullable = false)
  private Boolean enabled = true;

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

  public boolean isLive() {
    return ENV_LIVE.equals(environment);
  }

  public boolean isUsable() {
    return Boolean.TRUE.equals(enabled)
        && apiKey != null
        && !apiKey.isBlank()
        && webhookSecret != null
        && !webhookSecret.isBlank();
  }

  public UUID getId() {
    return id;
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID businessId) {
    this.businessId = businessId;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getWebhookSecret() {
    return webhookSecret;
  }

  public void setWebhookSecret(String webhookSecret) {
    this.webhookSecret = webhookSecret;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public String getWebhookToken() {
    return webhookToken;
  }

  public void setWebhookToken(String webhookToken) {
    this.webhookToken = webhookToken;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
