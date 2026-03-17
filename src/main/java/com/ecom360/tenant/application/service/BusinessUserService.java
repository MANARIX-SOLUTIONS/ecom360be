package com.ecom360.tenant.application.service;

import com.ecom360.identity.application.service.AuthService;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.shared.infrastructure.mail.EmailService;
import com.ecom360.store.application.dto.StoreResponse;
import com.ecom360.store.domain.model.Store;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.application.dto.AssignStoresRequest;
import com.ecom360.tenant.application.dto.BusinessUserResponse;
import com.ecom360.tenant.application.dto.InviteUserRequest;
import com.ecom360.tenant.domain.model.BusinessUserStore;
import com.ecom360.tenant.domain.repository.BusinessUserStoreRepository;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessUserService {

  private static final int TEMP_PASSWORD_LENGTH = 24;

  private final BusinessUserRepository businessUserRepository;
  private final BusinessUserStoreRepository businessUserStoreRepository;
  private final UserRepository userRepository;
  private final BusinessRepository businessRepository;
  private final StoreRepository storeRepository;
  private final PasswordEncoder passwordEncoder;
  private final SubscriptionService subscriptionService;
  private final AuthService authService;
  private final EmailService emailService;
  private final RolePermissionService permissionService;

  public BusinessUserService(
      BusinessUserRepository businessUserRepository,
      BusinessUserStoreRepository businessUserStoreRepository,
      UserRepository userRepository,
      BusinessRepository businessRepository,
      StoreRepository storeRepository,
      PasswordEncoder passwordEncoder,
      SubscriptionService subscriptionService,
      AuthService authService,
      EmailService emailService,
      RolePermissionService permissionService) {
    this.businessUserRepository = businessUserRepository;
    this.businessUserStoreRepository = businessUserStoreRepository;
    this.userRepository = userRepository;
    this.businessRepository = businessRepository;
    this.storeRepository = storeRepository;
    this.passwordEncoder = passwordEncoder;
    this.subscriptionService = subscriptionService;
    this.authService = authService;
    this.emailService = emailService;
    this.permissionService = permissionService;
  }

  public List<BusinessUserResponse> list(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.BUSINESS_USERS_READ);
    return businessUserRepository.findByBusinessIdAndIsActive(p.businessId(), true).stream()
        .map(
            bu -> {
              User u =
                  userRepository
                      .findById(bu.getUserId())
                      .orElseThrow(() -> new ResourceNotFoundException("User", bu.getUserId()));
              return new BusinessUserResponse(
                  bu.getId(),
                  bu.getUserId(),
                  u.getFullName(),
                  u.getEmail(),
                  bu.getRole(),
                  bu.getIsActive());
            })
        .toList();
  }

  @Transactional
  public BusinessUserResponse invite(InviteUserRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.BUSINESS_USERS_CREATE);
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!plan.isUnlimited(plan.getMaxUsers())) {
                long count =
                    businessUserRepository.findByBusinessIdAndIsActive(p.businessId(), true).size();
                if (count >= plan.getMaxUsers()) {
                  throw new BusinessRuleException(
                      "Limite du plan atteinte : maximum "
                          + plan.getMaxUsers()
                          + " utilisateur(s). Passez à un plan supérieur.");
                }
              }
              if (!Boolean.TRUE.equals(plan.getFeatureRoleManagement())) {
                String r = req.role() != null ? req.role().toLowerCase().trim() : "";
                if (!"caissier".equals(r)) {
                  throw new BusinessRuleException(
                      "Sur votre plan, seuls les comptes Caissier peuvent être invités. "
                          + "Passez au plan Business pour inviter des gestionnaires.");
                }
              }
            });
    User user = userRepository.findByEmail(req.email()).orElse(null);
    if (user == null) {
      String tempPassword = generateTempPassword();
      String tempName = req.email().contains("@") ? req.email().split("@")[0] : "Invité";
      user = User.create(tempName, req.email(), passwordEncoder.encode(tempPassword), null);
      user = userRepository.save(user);
      String rawToken = authService.createPasswordResetToken(user.getId());
      String setPasswordLink = emailService.buildResetPasswordLink(rawToken);
      Business business = businessRepository.findById(p.businessId()).orElse(null);
      String businessName = business != null ? business.getName() : "l'entreprise";
      emailService.sendInvitationEmail(
          user.getEmail(), user.getFullName(), businessName, setPasswordLink);
    }
    if (businessUserRepository.existsByBusinessIdAndUserId(p.businessId(), user.getId())) {
      throw new ResourceAlreadyExistsException("User", req.email() + " is already a member");
    }
    BusinessUser bu = BusinessUser.create(p.businessId(), user.getId(), req.role().toLowerCase());
    bu = businessUserRepository.save(bu);
    return new BusinessUserResponse(
        bu.getId(),
        bu.getUserId(),
        user.getFullName(),
        user.getEmail(),
        bu.getRole(),
        bu.getIsActive());
  }

  @Transactional
  public List<StoreResponse> assignStores(UUID businessUserId, AssignStoresRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.BUSINESS_USERS_UPDATE);
    BusinessUser bu = businessUserRepository.findById(businessUserId)
        .filter(b -> b.getBusinessId().equals(p.businessId()))
        .orElseThrow(() -> new ResourceNotFoundException("BusinessUser", businessUserId));
    businessUserStoreRepository.deleteByBusinessUserId(bu.getId());
    if (!req.storeIds().isEmpty()) {
      for (UUID storeId : req.storeIds()) {
        Store store = storeRepository.findById(storeId)
            .filter(s -> s.belongsTo(p.businessId()))
            .orElseThrow(() -> new ResourceNotFoundException("Store", storeId));
        businessUserStoreRepository.save(BusinessUserStore.create(bu.getId(), store.getId()));
      }
    }
    return getAssignedStores(businessUserId, p);
  }

  public List<StoreResponse> getAssignedStores(UUID businessUserId, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.BUSINESS_USERS_READ);
    BusinessUser bu = businessUserRepository.findById(businessUserId)
        .filter(b -> b.getBusinessId().equals(p.businessId()))
        .orElseThrow(() -> new ResourceNotFoundException("BusinessUser", businessUserId));
    return businessUserStoreRepository.findByBusinessUserId(bu.getId()).stream()
        .map(BusinessUserStore::getStoreId)
        .map(id -> storeRepository.findById(id).orElse(null))
        .filter(s -> s != null && s.belongsTo(p.businessId()))
        .map(s -> new StoreResponse(
            s.getId(), s.getBusinessId(), s.getName(), s.getAddress(),
            s.getPhone(), s.getIsActive(), s.getCreatedAt(), s.getUpdatedAt()))
        .toList();
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) {
      throw new AccessDeniedException("Business context required");
    }
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
}
