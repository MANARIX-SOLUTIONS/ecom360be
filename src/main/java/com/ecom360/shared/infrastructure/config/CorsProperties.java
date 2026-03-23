package com.ecom360.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised CORS settings so they can differ per profile. Bound from app.cors.* in
 * application-{profile}.yml.
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

  private String allowedOrigins = "*";
  private String allowedMethods = "GET,POST,PUT,PATCH,DELETE,OPTIONS";
  private String allowedHeaders = "*";
  private long maxAge = 3600;

  public String getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(String v) {
    this.allowedOrigins = v;
  }

  public String getAllowedMethods() {
    return allowedMethods;
  }

  public void setAllowedMethods(String v) {
    this.allowedMethods = v;
  }

  public String getAllowedHeaders() {
    return allowedHeaders;
  }

  public void setAllowedHeaders(String v) {
    this.allowedHeaders = v;
  }

  public long getMaxAge() {
    return maxAge;
  }

  public void setMaxAge(long v) {
    this.maxAge = v;
  }
}
