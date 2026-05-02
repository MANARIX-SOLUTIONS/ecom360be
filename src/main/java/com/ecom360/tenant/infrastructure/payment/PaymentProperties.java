package com.ecom360.tenant.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments")
public class PaymentProperties {

  private String provider = "paydunya";
  private String currency = "XOF";
  private String publicApiUrl = "http://localhost:8080";
  private String appUrl = "http://localhost:5173";

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getPublicApiUrl() {
    return publicApiUrl;
  }

  public void setPublicApiUrl(String publicApiUrl) {
    this.publicApiUrl = trimTrailingSlash(publicApiUrl);
  }

  public String getAppUrl() {
    return appUrl;
  }

  public void setAppUrl(String appUrl) {
    this.appUrl = trimTrailingSlash(appUrl);
  }

  private String trimTrailingSlash(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
