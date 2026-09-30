package com.ecom360.tenant.payment.infrastructure.bictorys;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.bictorys")
public class BictorysProperties {

  private boolean enabled = false;
  /** Sandbox: https://api.test.bictorys.com ; prod: https://api.bictorys.com */
  private String baseUrl = "https://api.test.bictorys.com";
  /** Public key (X-Api-Key) used for charges and status checks. */
  private String apiKey = "";
  /** Secret configured in Bictorys dashboard → Developers → Webhooks. */
  private String webhookSecret = "";
  /** Bictorys country code (SN, CI, ...). */
  private String country = "SN";
  /**
   * Public frontend URL used for success/error redirects (falls back to app.url).
   * Bictorys' WAF rejects localhost/private addresses with a 403.
   */
  private String redirectBaseUrl = "";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
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

  public String getRedirectBaseUrl() {
    return redirectBaseUrl;
  }

  public void setRedirectBaseUrl(String redirectBaseUrl) {
    this.redirectBaseUrl = redirectBaseUrl;
  }
}
