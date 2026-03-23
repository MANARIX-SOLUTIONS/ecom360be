package com.ecom360.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
  private String secret;
  private long expirationMs = 86400000;
  private long refreshExpirationMs = 604800000;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public long getExpirationMs() {
    return expirationMs;
  }

  public void setExpirationMs(long expirationMs) {
    this.expirationMs = expirationMs;
  }

  public long getRefreshExpirationMs() {
    return refreshExpirationMs;
  }

  public void setRefreshExpirationMs(long refreshExpirationMs) {
    this.refreshExpirationMs = refreshExpirationMs;
  }
}
