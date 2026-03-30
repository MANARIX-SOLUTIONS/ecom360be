package com.ecom360.tenant.application.service;

import com.ecom360.identity.domain.model.Permission;
import com.ecom360.tenant.domain.model.AppPermission;
import com.ecom360.tenant.domain.model.BusinessRole;
import com.ecom360.tenant.domain.model.BusinessRolePermission;
import com.ecom360.tenant.domain.repository.AppPermissionRepository;
import com.ecom360.tenant.domain.repository.BusinessRolePermissionRepository;
import com.ecom360.tenant.domain.repository.BusinessRoleRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée les rôles système (PROPRIETAIRE, GESTIONNAIRE, CAISSIER) et leurs permissions pour une
 * entreprise — utilisé à l'inscription ou par l'admin plateforme lorsque la base est vide pour ce
 * tenant.
 */
@Service
public class BusinessRoleBootstrapService {

  private static final Set<Permission> MANAGER_EXCLUDED =
      EnumSet.of(Permission.SUBSCRIPTION_UPDATE, Permission.BUSINESS_USERS_DELETE);

  private static final Set<Permission> CAISSIER_PERMISSIONS =
      EnumSet.of(
          Permission.PRODUCTS_READ,
          Permission.CATEGORIES_READ,
          Permission.STOCK_READ,
          Permission.CLIENTS_READ,
          Permission.STORES_READ,
          Permission.SALES_CREATE,
          Permission.SALES_READ,
          Permission.SALES_DELETE,
          Permission.REPORTS_READ);

  private final BusinessRoleRepository businessRoleRepository;
  private final AppPermissionRepository appPermissionRepository;
  private final BusinessRolePermissionRepository businessRolePermissionRepository;

  public BusinessRoleBootstrapService(
      BusinessRoleRepository businessRoleRepository,
      AppPermissionRepository appPermissionRepository,
      BusinessRolePermissionRepository businessRolePermissionRepository) {
    this.businessRoleRepository = businessRoleRepository;
    this.appPermissionRepository = appPermissionRepository;
    this.businessRolePermissionRepository = businessRolePermissionRepository;
  }

  @Transactional
  public void ensureDefaultRolesForBusiness(UUID businessId) {
    if (businessRoleRepository.countByBusinessId(businessId) > 0) {
      return;
    }
    List<AppPermission> allRows = appPermissionRepository.findAllByOrderByCodeAsc();
    BusinessRole proprietaire =
        businessRoleRepository.save(
            BusinessRole.createSystem(businessId, "PROPRIETAIRE", "Propriétaire", true));
    BusinessRole gestionnaire =
        businessRoleRepository.save(
            BusinessRole.createSystem(businessId, "GESTIONNAIRE", "Gestionnaire", true));
    BusinessRole caissier =
        businessRoleRepository.save(
            BusinessRole.createSystem(businessId, "CAISSIER", "Caissier", true));

    for (AppPermission ap : allRows) {
      businessRolePermissionRepository.save(BusinessRolePermission.link(proprietaire, ap));
    }
    for (AppPermission ap : allRows) {
      Permission perm = Permission.valueOf(ap.getCode());
      if (!MANAGER_EXCLUDED.contains(perm)) {
        businessRolePermissionRepository.save(BusinessRolePermission.link(gestionnaire, ap));
      }
    }
    for (Permission p : CAISSIER_PERMISSIONS) {
      AppPermission ap =
          appPermissionRepository
              .findByCode(p.name())
              .orElseThrow(() -> new IllegalStateException("Missing permission: " + p.name()));
      businessRolePermissionRepository.save(BusinessRolePermission.link(caissier, ap));
    }
  }
}
