package com.ecom360.identity.application.service;

import com.ecom360.tenant.domain.repository.BusinessRolePermissionRepository;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Cache en mémoire des codes permission par rôle (invalidé lors des mises à jour). */
@Component
public class CachedRolePermissions {

  private final ConcurrentHashMap<UUID, Set<String>> cache = new ConcurrentHashMap<>();
  private final BusinessRolePermissionRepository businessRolePermissionRepository;

  public CachedRolePermissions(BusinessRolePermissionRepository businessRolePermissionRepository) {
    this.businessRolePermissionRepository = businessRolePermissionRepository;
  }

  public Set<String> codesForRole(UUID roleId) {
    if (roleId == null) {
      return Set.of();
    }
    return cache.computeIfAbsent(roleId, this::load);
  }

  public void evict(UUID roleId) {
    if (roleId != null) {
      cache.remove(roleId);
    }
  }

  private Set<String> load(UUID roleId) {
    return Set.copyOf(businessRolePermissionRepository.findPermissionCodesByRoleId(roleId));
  }
}
