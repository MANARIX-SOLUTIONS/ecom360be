package com.ecom360.shared.infrastructure.web;

public final class ApiConstants {

  public static final String API_BASE = "/api/v1";
  public static final String X_BUSINESS_ID = "X-Business-Id";
  public static final String X_REQUEST_ID = "X-Request-Id";

  /**
   * HMAC-SHA256 (hex) du corps brut UTF-8, secret partagé affiché une fois à la création de la
   * connexion.
   */
  public static final String X_COMMERCE_SIGNATURE = "X-Commerce-Signature";

  public static final String AUTHORIZATION = "Authorization";
  public static final String BEARER_PREFIX = "Bearer ";

  public static final int DEFAULT_PAGE_SIZE = 20;
  public static final int MAX_PAGE_SIZE = 100;

  private ApiConstants() {}
}
