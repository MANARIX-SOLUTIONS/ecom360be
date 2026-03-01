package com.ecom360.identity.application.service;

import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Active le périmètre des fonctionnalités selon le rôle utilisateur : - propriétaire : accès
 * complet - gestionnaire : accès large (sauf abonnement, suppression propriétaire) - caissier :
 * ventes, lecture produits/clients/stock/boutiques
 */
@Service
public class RolePermissionService {

  private static final Set<Permission> PROPRIETAIRE = EnumSet.allOf(Permission.class);

  private static final Set<Permission> GESTIONNAIRE =
      EnumSet.complementOf(
          EnumSet.of(
              Permission.SUBSCRIPTION_UPDATE,
              Permission.BUSINESS_USERS_DELETE));

  private static final Set<Permission> CAISSIER =
      EnumSet.of(
          Permission.PRODUCTS_READ,
          Permission.CATEGORIES_READ,
          Permission.STOCK_READ,
          Permission.CLIENTS_READ,
          Permission.STORES_READ,
          Permission.SALES_CREATE,
          Permission.SALES_READ,
          Permission.SALES_UPDATE,
          Permission.SALES_DELETE);
  // GLOBAL_VIEW_READ : propriétaire et gestionnaire uniquement (pas caissier)

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

    String role = p.role() != null ? p.role().toLowerCase() : "";
    Set<Permission> allowed =
        switch (role) {
          case "proprietaire", "propriétaire" -> PROPRIETAIRE;
          case "gestionnaire" -> GESTIONNAIRE;
          case "caissier" -> CAISSIER;
          default -> Set.of();
        };
    return allowed.contains(perm);
  }
}
