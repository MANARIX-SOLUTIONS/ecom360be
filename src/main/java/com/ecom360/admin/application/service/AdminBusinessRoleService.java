package com.ecom360.admin.application.service;

import com.ecom360.admin.application.dto.AdminBusinessRoleOptionResponse;
import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.identity.application.service.CachedRolePermissions;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.dto.AssignRolePermissionsRequest;
import com.ecom360.tenant.domain.model.AppPermission;
import com.ecom360.tenant.domain.model.BusinessRole;
import com.ecom360.tenant.domain.model.BusinessRolePermission;
import com.ecom360.tenant.domain.repository.AppPermissionRepository;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessRolePermissionRepository;
import com.ecom360.tenant.domain.repository.BusinessRoleRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBusinessRoleService {

  private final BusinessRepository businessRepository;
  private final BusinessRoleRepository businessRoleRepository;
  private final AppPermissionRepository appPermissionRepository;
  private final BusinessRolePermissionRepository businessRolePermissionRepository;
  private final CachedRolePermissions cachedRolePermissions;
  private final AuditLogService auditLogService;

  public AdminBusinessRoleService(
      BusinessRepository businessRepository,
      BusinessRoleRepository businessRoleRepository,
      AppPermissionRepository appPermissionRepository,
      BusinessRolePermissionRepository businessRolePermissionRepository,
      CachedRolePermissions cachedRolePermissions,
      AuditLogService auditLogService) {
    this.businessRepository = businessRepository;
    this.businessRoleRepository = businessRoleRepository;
    this.appPermissionRepository = appPermissionRepository;
    this.businessRolePermissionRepository = businessRolePermissionRepository;
    this.cachedRolePermissions = cachedRolePermissions;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public AdminBusinessRoleOptionResponse updateRolePermissions(
      UUID businessId, UUID roleId, AssignRolePermissionsRequest req, UserPrincipal p) {
    requirePlatformAdmin(p);
    businessRepository
        .findById(businessId)
        .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    BusinessRole role =
        businessRoleRepository
            .findById(roleId)
            .filter(r -> r.getBusinessId().equals(businessId))
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
    AdminBusinessRoleOptionResponse response = toOptionResponse(role);
    Map<String, Object> auditChanges = new LinkedHashMap<>();
    auditChanges.put("roleCode", role.getCode());
    auditChanges.put("roleName", role.getName());
    auditChanges.put("permissionCodes", response.permissions());
    auditChanges.put("source", "platform_admin");
    auditLogService.logAsync(
        businessId, p.userId(), "UPDATE", "BusinessRole", roleId, auditChanges);
    return response;
  }

  private AdminBusinessRoleOptionResponse toOptionResponse(BusinessRole role) {
    List<String> codes =
        businessRolePermissionRepository.findPermissionCodesByRoleId(role.getId()).stream()
            .sorted()
            .toList();
    return new AdminBusinessRoleOptionResponse(
        role.getId(), role.getCode(), role.getName(), role.isSystem(), codes);
  }

  private static void requirePlatformAdmin(UserPrincipal p) {
    if (p == null || !p.isPlatformAdmin()) {
      throw new AccessDeniedException("Platform admin only");
    }
  }
}
