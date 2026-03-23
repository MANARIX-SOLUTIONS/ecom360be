package com.ecom360.identity.infrastructure.security;

import java.util.UUID;

/** Authenticated user context including business scope for multi-tenancy. */
public record UserPrincipal(
    UUID userId, String email, UUID businessId, String role, UUID roleId, boolean platformAdmin) {

  public boolean isPlatformAdmin() {
    return platformAdmin || "PLATFORM_ADMIN".equalsIgnoreCase(role);
  }

  public boolean hasBusinessAccess() {
    return businessId != null || isPlatformAdmin();
  }
}
