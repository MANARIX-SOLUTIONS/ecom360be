package com.ecom360.admin.application.service;

import com.ecom360.admin.application.dto.AdminBusinessMemberResponse;
import com.ecom360.admin.application.dto.AdminBusinessRoleOptionResponse;
import com.ecom360.admin.application.dto.AdminUpdateMemberRoleRequest;
import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.domain.model.BusinessRole;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessRolePermissionRepository;
import com.ecom360.tenant.domain.repository.BusinessRoleRepository;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBusinessMemberService {

  private final BusinessRepository businessRepository;
  private final BusinessUserRepository businessUserRepository;
  private final BusinessRoleRepository businessRoleRepository;
  private final BusinessRolePermissionRepository businessRolePermissionRepository;
  private final UserRepository userRepository;
  private final AuditLogService auditLogService;

  public AdminBusinessMemberService(
      BusinessRepository businessRepository,
      BusinessUserRepository businessUserRepository,
      BusinessRoleRepository businessRoleRepository,
      BusinessRolePermissionRepository businessRolePermissionRepository,
      UserRepository userRepository,
      AuditLogService auditLogService) {
    this.businessRepository = businessRepository;
    this.businessUserRepository = businessUserRepository;
    this.businessRoleRepository = businessRoleRepository;
    this.businessRolePermissionRepository = businessRolePermissionRepository;
    this.userRepository = userRepository;
    this.auditLogService = auditLogService;
  }

  public List<AdminBusinessMemberResponse> listMembers(UUID businessId, UserPrincipal p) {
    requirePlatformAdmin(p);
    businessRepository
        .findById(businessId)
        .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    return businessUserRepository.findByBusinessIdOrderByCreatedAtWithRole(businessId).stream()
        .map(this::toMemberResponse)
        .toList();
  }

  public List<AdminBusinessRoleOptionResponse> listRoleOptions(UUID businessId, UserPrincipal p) {
    requirePlatformAdmin(p);
    businessRepository
        .findById(businessId)
        .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    return businessRoleRepository.findByBusinessIdOrderByCodeAsc(businessId).stream()
        .map(
            r ->
                new AdminBusinessRoleOptionResponse(
                    r.getId(),
                    r.getCode(),
                    r.getName(),
                    r.isSystem(),
                    permissionCodesSorted(r.getId())))
        .toList();
  }

  @Transactional
  public AdminBusinessMemberResponse updateMemberRole(
      UUID businessId, UUID businessUserId, AdminUpdateMemberRoleRequest req, UserPrincipal p) {
    requirePlatformAdmin(p);
    businessRepository
        .findById(businessId)
        .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    BusinessUser bu =
        businessUserRepository
            .findByIdWithRole(businessUserId)
            .orElseThrow(() -> new ResourceNotFoundException("BusinessUser", businessUserId));
    if (!bu.getBusinessId().equals(businessId)) {
      throw new ResourceNotFoundException("BusinessUser", businessUserId);
    }
    User memberUser =
        userRepository
            .findById(bu.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", bu.getUserId()));
    String code = req.roleCode().trim().toUpperCase();
    BusinessRole newRole =
        businessRoleRepository
            .findByBusinessIdAndCode(businessId, code)
            .orElseThrow(() -> new ResourceNotFoundException("Role", code));

    String currentCode = bu.getBusinessRole().getCode();
    if ("PROPRIETAIRE".equalsIgnoreCase(currentCode) && !"PROPRIETAIRE".equalsIgnoreCase(code)) {
      long adminCount =
          businessUserRepository.findByBusinessIdOrderByCreatedAtWithRole(businessId).stream()
              .filter(BusinessUser::isActive)
              .filter(x -> "PROPRIETAIRE".equalsIgnoreCase(x.getBusinessRole().getCode()))
              .count();
      if (adminCount <= 1) {
        throw new BusinessRuleException(
            "Impossible de retirer le dernier rôle Administrateur de cette entreprise.");
      }
    }

    bu.setBusinessRole(newRole);
    businessUserRepository.save(bu);
    AdminBusinessMemberResponse out =
        toMemberResponse(businessUserRepository.findByIdWithRole(businessUserId).orElseThrow());
    Map<String, Object> auditChanges = new LinkedHashMap<>();
    auditChanges.put("previousRoleCode", currentCode);
    auditChanges.put("newRoleCode", newRole.getCode());
    auditChanges.put("memberEmail", memberUser.getEmail());
    auditChanges.put("source", "platform_admin");
    auditLogService.logAsync(
        businessId, p.userId(), "UPDATE", "BusinessUser", businessUserId, auditChanges);
    return out;
  }

  private AdminBusinessMemberResponse toMemberResponse(BusinessUser bu) {
    User u =
        userRepository
            .findById(bu.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", bu.getUserId()));
    return new AdminBusinessMemberResponse(
        bu.getId(),
        bu.getUserId(),
        u.getFullName(),
        u.getEmail(),
        bu.getBusinessRole().getCode(),
        bu.getBusinessRole().getName(),
        bu.isActive(),
        permissionCodesSorted(bu.getBusinessRole().getId()));
  }

  private List<String> permissionCodesSorted(UUID roleId) {
    return businessRolePermissionRepository.findPermissionCodesByRoleId(roleId).stream()
        .sorted()
        .toList();
  }

  private static void requirePlatformAdmin(UserPrincipal p) {
    if (p == null || !p.isPlatformAdmin()) {
      throw new AccessDeniedException("Platform admin only");
    }
  }
}
