package com.ecom360.admin.application.service;

import com.ecom360.admin.application.dto.AdminInviteRequest;
import com.ecom360.admin.application.dto.AdminUserResponse;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.BusinessRole;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessRoleRepository;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.tenant.domain.repository.PlanRepository;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

  private static final int TEMP_PASSWORD_LENGTH = 24;

  private final UserRepository userRepository;
  private final BusinessUserRepository businessUserRepository;
  private final BusinessRepository businessRepository;
  private final PasswordEncoder passwordEncoder;
  private final SubscriptionRepository subscriptionRepository;
  private final PlanRepository planRepository;
  private final BusinessRoleRepository businessRoleRepository;

  public AdminUserService(
      UserRepository userRepository,
      BusinessUserRepository businessUserRepository,
      BusinessRepository businessRepository,
      PasswordEncoder passwordEncoder,
      SubscriptionRepository subscriptionRepository,
      PlanRepository planRepository,
      BusinessRoleRepository businessRoleRepository) {
    this.userRepository = userRepository;
    this.businessUserRepository = businessUserRepository;
    this.businessRepository = businessRepository;
    this.passwordEncoder = passwordEncoder;
    this.subscriptionRepository = subscriptionRepository;
    this.planRepository = planRepository;
    this.businessRoleRepository = businessRoleRepository;
  }

  public Page<AdminUserResponse> list(
      UserPrincipal p, int page, int size, String search, String status, String role) {
    int pageSize = Math.min(size, 100);
    Pageable pageable = PageRequest.of(page, pageSize);
    String q = (search != null && !search.isBlank()) ? search.trim() : null;
    Boolean active = null;
    if (status != null && "active".equals(status.trim())) active = true;
    else if (status != null
        && ("disabled".equals(status.trim()) || "inactive".equals(status.trim()))) active = false;
    String roleSlug = mapRoleToSlug(role);

    Page<User> users;
    if (q == null && active == null && roleSlug == null) {
      users = userRepository.findAllByOrderByCreatedAtDesc(pageable);
    } else {
      users = userRepository.searchByNameEmailOrBusiness(q, active, roleSlug, pageable);
    }

    List<UUID> userIds = users.getContent().stream().map(User::getId).toList();
    Map<UUID, String> roleMap = loadPrimaryRole(userIds);
    Map<UUID, String> businessMap = loadPrimaryBusiness(userIds);

    return users.map(u -> map(u, roleMap, businessMap));
  }

  private Map<UUID, String> loadPrimaryRole(List<UUID> userIds) {
    return userIds.stream()
        .flatMap(uid -> businessUserRepository.findByUserId(uid).stream().findFirst().stream())
        .collect(
            Collectors.toMap(
                BusinessUser::getUserId,
                bu -> formatRole(bu.getBusinessRole().getCode()),
                (a, b) -> a));
  }

  private Map<UUID, String> loadPrimaryBusiness(List<UUID> userIds) {
    return userIds.stream()
        .flatMap(uid -> businessUserRepository.findByUserId(uid).stream().findFirst().stream())
        .collect(
            Collectors.toMap(
                BusinessUser::getUserId,
                bu -> {
                  Business b = businessRepository.findById(bu.getBusinessId()).orElse(null);
                  return b != null ? b.getName() : "-";
                },
                (a, b) -> a));
  }

  private String mapRoleToSlug(String role) {
    if (role == null || role.isBlank() || "all".equalsIgnoreCase(role)) return null;
    return switch (role.toLowerCase()) {
      case "propriétaire", "proprietaire", "admin" -> "PROPRIETAIRE";
      case "gestionnaire", "manager" -> "GESTIONNAIRE";
      case "caissier", "seller" -> "CAISSIER";
      default -> role.toUpperCase();
    };
  }

  private String formatRole(String roleCode) {
    if (roleCode == null) return "-";
    return switch (roleCode.toUpperCase()) {
      case "PROPRIETAIRE", "ADMIN" -> "Propriétaire";
      case "GESTIONNAIRE", "MANAGER" -> "Gestionnaire";
      case "CAISSIER", "SELLER" -> "Caissier";
      default -> roleCode;
    };
  }

  private AdminUserResponse map(User u, Map<UUID, String> roleMap, Map<UUID, String> businessMap) {
    String status = u.isActive() ? "active" : "disabled";
    return new AdminUserResponse(
        u.getId(),
        u.getFullName(),
        u.getEmail(),
        roleMap.getOrDefault(u.getId(), "-"),
        businessMap.getOrDefault(u.getId(), "-"),
        status,
        u.getLastLoginAt(),
        u.getCreatedAt());
  }

  @Transactional
  public AdminUserResponse invite(AdminInviteRequest req, UserPrincipal p) {
    Business business =
        businessRepository
            .findById(req.businessId())
            .orElseThrow(() -> new ResourceNotFoundException("Business", req.businessId()));

    Optional<Subscription> subOpt =
        subscriptionRepository.findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(
            req.businessId(), List.of("active", "trialing"));
    subOpt
        .flatMap(sub -> planRepository.findById(sub.getPlanId()))
        .ifPresent(
            plan -> {
              if (!plan.isUnlimited(plan.getMaxUsers())) {
                int count =
                    businessUserRepository
                        .findByBusinessIdAndIsActive(req.businessId(), true)
                        .size();
                if (count >= plan.getMaxUsers()) {
                  throw new BusinessRuleException(
                      "Limite du plan atteinte : maximum "
                          + plan.getMaxUsers()
                          + " utilisateur(s) pour cette entreprise.");
                }
              }
            });

    User user = userRepository.findByEmail(req.email()).orElse(null);
    if (user == null) {
      String tempPassword = generateTempPassword();
      user = User.create(req.fullName(), req.email(), passwordEncoder.encode(tempPassword), null);
      user = userRepository.save(user);
    }

    if (businessUserRepository.existsByBusinessIdAndUserId(req.businessId(), user.getId())) {
      throw new ResourceAlreadyExistsException(
          "User", req.email() + " est déjà membre de cette entreprise");
    }

    String mappedRole = mapRoleToSlug(req.role());
    final String resolvedRoleCode = mappedRole != null ? mappedRole : "CAISSIER";
    BusinessRole role =
        businessRoleRepository
            .findByBusinessIdAndCode(req.businessId(), resolvedRoleCode)
            .orElseThrow(() -> new ResourceNotFoundException("Role", resolvedRoleCode));
    BusinessUser bu = BusinessUser.create(req.businessId(), user.getId(), role);
    bu = businessUserRepository.save(bu);

    return new AdminUserResponse(
        user.getId(),
        user.getFullName(),
        user.getEmail(),
        formatRole(bu.getBusinessRole().getCode()),
        business.getName(),
        user.isActive() ? "active" : "disabled",
        user.getLastLoginAt(),
        user.getCreatedAt());
  }

  private static String generateTempPassword() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[TEMP_PASSWORD_LENGTH];
    random.nextBytes(bytes);
    return Base64.getEncoder()
        .encodeToString(bytes)
        .replaceAll("[^A-Za-z0-9]", "")
        .substring(0, TEMP_PASSWORD_LENGTH);
  }

  public void setStatus(UUID userId, boolean active, UserPrincipal p) {
    User u =
        userRepository
            .findById(userId)
            .orElseThrow(
                () ->
                    new com.ecom360.shared.domain.exception.ResourceNotFoundException(
                        "User", userId));
    if (active) {
      u.activate();
    } else {
      u.deactivate();
    }
    userRepository.save(u);
  }
}
