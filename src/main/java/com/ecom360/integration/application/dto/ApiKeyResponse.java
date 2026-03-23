package com.ecom360.integration.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApiKeyResponse(
    UUID id,
    UUID businessId,
    String label,
    String permissions,
    LocalDate expiresAt,
    Boolean isActive,
    Instant createdAt,
    String rawKey) {
  public static ApiKeyResponse withoutRawKey(
      UUID id,
      UUID businessId,
      String label,
      String permissions,
      LocalDate expiresAt,
      Boolean isActive,
      Instant createdAt) {
    return new ApiKeyResponse(
        id, businessId, label, permissions, expiresAt, isActive, createdAt, null);
  }
}
