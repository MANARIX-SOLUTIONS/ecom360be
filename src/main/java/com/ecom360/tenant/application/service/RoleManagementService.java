package com.ecom360.tenant.application.service;

import com.ecom360.identity.application.service.CachedRolePermissions;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.dto.AssignRolePermissionsRequest;
import com.ecom360.tenant.application.dto.BusinessRoleResponse;
import com.ecom360.tenant.application.dto.CreateBusinessRoleRequest;
import com.ecom360.tenant.domain.model.AppPermission;
import com.ecom360.tenant.domain.model.BusinessRole;
import com.ecom360.tenant.domain.model.BusinessRolePermission;
import com.ecom360.tenant.domain.repository.AppPermissionRepository;
import com.ecom360.tenant.domain.repository.BusinessRolePermissionRepository;
import com.ecom360.tenant.domain.repository.BusinessRoleRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {

  private final BusinessRoleRepository businessRoleRepository;
  private final AppPermissionRepository appPermissionRepository;
  private final BusinessRolePermissionRepository businessRolePermissionRepository;
  private final RolePermissionService rolePermissionService;
  private final CachedRolePermissions cachedRolePermissions;

  public RoleManagementService(
      BusinessRoleRepository businessRoleRepository,
      AppPermissionRepository appPermissionRepository,
      BusinessRolePermissionRepository businessRolePermissionRepository,
      RolePermissionService rolePermissionService,
      CachedRolePermissions cachedRolePermissions) {
    this.businessRoleRepository = businessRoleRepository;
    this.appPermissionRepository = appPermissionRepository;
    this.businessRolePermissionRepository = businessRolePermissionRepository;
    this.rolePermissionService = rolePermissionService;
    this.cachedRolePermissions = cachedRolePermissions;
  }

  public List<BusinessRoleResponse> list(UserPrincipal p) {
    requireBiz(p);
    rolePermissionService.require(p, Permission.BUSINESS_USERS_READ);
    UUID bid = p.businessId();
    return businessRoleRepository.findByBusinessIdOrderByCodeAsc(bid).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public BusinessRoleResponse create(CreateBusinessRoleRequest req, UserPrincipal p) {
    requireBiz(p);
    rolePermissionService.require(p, Permission.BUSINESS_USERS_UPDATE);
    String name = req.name() != null ? req.name().trim() : "";
    if (name.isEmpty()) {
      throw new BusinessRuleException("Le nom du rôle est requis");
    }
    UUID bid = p.businessId();
    String code = generateUniqueCode(bid, name);
    BusinessRole role = businessRoleRepository.save(BusinessRole.createCustom(bid, code, name));
    return toResponse(role);
  }

  @Transactional
  public BusinessRoleResponse assignPermissions(
      UUID roleId, AssignRolePermissionsRequest req, UserPrincipal p) {
    requireBiz(p);
    rolePermissionService.require(p, Permission.BUSINESS_USERS_UPDATE);
    BusinessRole role =
        businessRoleRepository
            .findById(roleId)
            .filter(r -> r.getBusinessId().equals(p.businessId()))
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
    businessRolePermissionRepository.deleteByRoleId(roleId);
    for (String code : Objects.requireNonNullElse(req.permissionCodes(), List.<String>of())) {
      AppPermission ap =
          appPermissionRepository
              .findByCode(code.trim())
              .orElseThrow(() -> new BusinessRuleException("Permission inconnue: " + code));
      businessRolePermissionRepository.save(BusinessRolePermission.link(role, ap));
    }
    cachedRolePermissions.evict(roleId);
    return toResponse(role);
  }

  private BusinessRoleResponse toResponse(BusinessRole role) {
    List<String> codes = businessRolePermissionRepository.findPermissionCodesByRoleId(role.getId());
    return new BusinessRoleResponse(
        role.getId(), role.getBusinessId(), role.getCode(), role.getName(), role.isSystem(), codes);
  }

  private String generateUniqueCode(UUID businessId, String name) {
    String base =
        "CUSTOM_"
            + name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    if (base.length() > 40) {
      base = base.substring(0, 40);
    }
    if (base.equals("CUSTOM_") || base.isEmpty()) {
      base = "CUSTOM_ROLE";
    }
    String candidate = base;
    int n = 0;
    while (businessRoleRepository.existsByBusinessIdAndCode(businessId, candidate)) {
      n++;
      candidate = base + "_" + n;
    }
    return candidate;
  }

  private static void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) {
      throw new AccessDeniedException("Business context required");
    }
  }
}
