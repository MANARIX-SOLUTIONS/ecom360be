package com.ecom360.identity.application.service;

import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Active le périmètre des fonctionnalités selon le rôle utilisateur : - propriétaire : accès
 * complet - gestionnaire : accès large (sauf abonnement, suppression propriétaire) - caissier :
 * ventes, lecture produits/clients/stock/boutiques
 */
@Service
public class RolePermissionService {

  private final CachedRolePermissions cachedRolePermissions;

  public RolePermissionService(CachedRolePermissions cachedRolePermissions) {
    this.cachedRolePermissions = cachedRolePermissions;
  }

  /** Vérifie que l'utilisateur a la permission. Lance AccessDeniedException sinon. */
  public void require(UserPrincipal p, Permission perm) {
    if (!can(p, perm)) {
      throw new AccessDeniedException(
          "Accès refusé : permission " + perm + " requise pour votre rôle");
    }
  }

  /** Retourne true si l'utilisateur a la permission. */
  public boolean can(UserPrincipal p, Permission perm) {
    if (p == null) return false;
    if (p.isPlatformAdmin()) return true;
    if (!p.hasBusinessAccess()) return false;
    if (p.roleId() == null) return false;
    return cachedRolePermissions.codesForRole(p.roleId()).contains(perm.name());
  }
}
