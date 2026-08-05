package com.ecom360.tenant.payment.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.paydunya")
public class PaydunyaProperties {

  private boolean enabled = false;
  private String baseUrl = "https://app.paydunya.com";
  private String masterKey = "";
  private String privateKey = "";
  private String token = "";
  private String storeName = "Ecom 360 PME";
  /** Public API base used for IPN callback, e.g. https://api.example.com */
  private String apiPublicUrl = "http://localhost:8080";

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

  public String getMasterKey() {
    return masterKey;
  }

  public void setMasterKey(String masterKey) {
    this.masterKey = masterKey;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public void setPrivateKey(String privateKey) {
    this.privateKey = privateKey;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getStoreName() {
    return storeName;
  }

  public void setStoreName(String storeName) {
    this.storeName = storeName;
  }

  public String getApiPublicUrl() {
    return apiPublicUrl;
  }

  public void setApiPublicUrl(String apiPublicUrl) {
    this.apiPublicUrl = apiPublicUrl;
  }
}
