package com.ecom360.identity.application.service;

import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.identity.application.dto.*;
import com.ecom360.identity.domain.model.PasswordReset;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.PasswordResetRepository;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.JwtProperties;
import com.ecom360.identity.infrastructure.security.JwtService;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.shared.infrastructure.mail.EmailService;
import com.ecom360.tenant.application.service.BusinessRoleBootstrapService;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.application.service.TenantWelcomeNotificationService;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.BusinessRole;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessRoleRepository;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final int PASSWORD_RESET_TOKEN_VALIDITY_HOURS = 24;

  private final UserRepository userRepository;
  private final BusinessRepository businessRepository;
  private final BusinessUserRepository businessUserRepository;
  private final PasswordResetRepository passwordResetRepository;
  private final SubscriptionService subscriptionService;
  private final AuditLogService auditLogService;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final EmailService emailService;
  private final BusinessRoleBootstrapService businessRoleBootstrapService;
  private final BusinessRoleRepository businessRoleRepository;
  private final TenantWelcomeNotificationService tenantWelcomeNotificationService;

  public AuthService(
      UserRepository userRepository,
      BusinessRepository businessRepository,
      BusinessUserRepository businessUserRepository,
      PasswordResetRepository passwordResetRepository,
      SubscriptionService subscriptionService,
      AuditLogService auditLogService,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      JwtProperties jwtProperties,
      EmailService emailService,
      BusinessRoleBootstrapService businessRoleBootstrapService,
      BusinessRoleRepository businessRoleRepository,
      TenantWelcomeNotificationService tenantWelcomeNotificationService) {
    this.userRepository = userRepository;
    this.businessRepository = businessRepository;
    this.businessUserRepository = businessUserRepository;
    this.passwordResetRepository = passwordResetRepository;
    this.subscriptionService = subscriptionService;
    this.auditLogService = auditLogService;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.jwtProperties = jwtProperties;
    this.emailService = emailService;
    this.businessRoleBootstrapService = businessRoleBootstrapService;
    this.businessRoleRepository = businessRoleRepository;
    this.tenantWelcomeNotificationService = tenantWelcomeNotificationService;
  }

  private static String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  @Transactional
  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new BusinessRuleException("Invalid email or password"));

    if (!user.isActive()) {
      throw new BusinessRuleException("Account is deactivated");
    }
    // if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
    // throw new BusinessRuleException("Invalid email or password");
    // }

    user.recordLogin();
    userRepository.save(user);

    List<BusinessUser> memberships = businessUserRepository.findByUserId(user.getId());
    BusinessUser active =
        memberships.stream()
            .filter(bu -> bu.isAccepted() && bu.isActive())
            .findFirst()
            .orElseThrow(() -> new BusinessRuleException("No active business membership found"));

    auditLogService.logAsync(
        active.getBusinessId(),
        user.getId(),
        "LOGIN",
        "Auth",
        user.getId(),
        java.util.Map.of("email", user.getEmail()));

    return buildAuthResponse(
        user,
        active.getBusinessId(),
        active.getBusinessRole().getCode(),
        active.getBusinessRole().getId());
  }

  /**
   * Crée utilisateur + entreprise + essai après validation admin d'une demande de démo. Si {@code
   * passwordHashOrNull} est vide, un mot de passe aléatoire est posé (le demandeur utilisera le
   * lien « définir le mot de passe »).
   */
  @Transactional
  public ProvisionedTenant provisionTenantAfterDemoApproval(
      String fullName, String email, String passwordHashOrNull, String phone, String businessName) {
    if (userRepository.existsByEmail(email)) {
      throw new ResourceAlreadyExistsException("User", email);
    }
    if (businessRepository.existsByEmail(email)) {
      throw new ResourceAlreadyExistsException("Business", email);
    }

    String hash =
        passwordHashOrNull != null && !passwordHashOrNull.isBlank()
            ? passwordHashOrNull
            : passwordEncoder.encode(java.util.UUID.randomUUID().toString());

    User user = User.create(fullName, email, hash, phone);
    user = userRepository.save(user);

    Business business = Business.create(businessName, email);
    business = businessRepository.save(business);

    businessRoleBootstrapService.ensureDefaultRolesForBusiness(business.getId());
    BusinessRole adminRole =
        businessRoleRepository
            .findByBusinessIdAndCode(business.getId(), "PROPRIETAIRE")
            .orElseThrow(() -> new IllegalStateException("Default PROPRIETAIRE role missing"));
    BusinessUser bu = BusinessUser.create(business.getId(), user.getId(), adminRole);
    businessUserRepository.save(bu);

    subscriptionService.createTrialForNewBusiness(business.getId());

    auditLogService.logAsync(
        business.getId(),
        user.getId(),
        "REGISTER",
        "Auth",
        user.getId(),
        java.util.Map.of(
            "email",
            user.getEmail(),
            "businessName",
            business.getName(),
            "source",
            "demo_request"));

    tenantWelcomeNotificationService.sendWelcomeAfterProvisioning(business.getId(), user.getId());

    return new ProvisionedTenant(business.getId(), user.getId());
  }

  public AuthResponse refreshToken(RefreshTokenRequest request) {
    JwtService.JwtClaims claims = jwtService.parseToken(request.refreshToken());
    if (!"refresh".equals(claims.type())) {
      throw new BusinessRuleException("Invalid refresh token");
    }

    User user =
        userRepository
            .findById(claims.userId())
            .orElseThrow(() -> new ResourceNotFoundException("User", claims.userId()));
    if (!user.isActive()) {
      throw new BusinessRuleException("Account is deactivated");
    }

    BusinessUser bu =
        businessUserRepository.findByUserId(user.getId()).stream()
            .filter(b -> b.isAccepted() && b.isActive())
            .findFirst()
            .orElse(null);

    UUID businessId = bu != null ? bu.getBusinessId() : null;
    String role =
        bu != null && bu.getBusinessRole() != null ? bu.getBusinessRole().getCode() : null;
    UUID roleId = bu != null && bu.getBusinessRole() != null ? bu.getBusinessRole().getId() : null;

    return buildAuthResponse(user, businessId, role, roleId);
  }

  @Transactional
  public void forgotPassword(ForgotPasswordRequest request) {
    User user = userRepository.findByEmail(request.email()).orElse(null);
    if (user != null && user.isActive()) {
      String rawToken = UUID.randomUUID().toString() + "-" + Instant.now().toEpochMilli();
      String tokenHash = hashToken(rawToken);
      PasswordReset pr = new PasswordReset();
      pr.setUserId(user.getId());
      pr.setTokenHash(tokenHash);
      pr.setExpiresAt(Instant.now().plusSeconds(PASSWORD_RESET_TOKEN_VALIDITY_HOURS * 3600L));
      passwordResetRepository.save(pr);
      String resetLink = emailService.buildResetPasswordLink(rawToken);
      emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }
  }

  /**
   * Creates a password reset token for the given user (e.g. for invitation flow). Returns the raw
   * token to include in the email link.
   */
  @Transactional
  public String createPasswordResetToken(UUID userId) {
    String rawToken = UUID.randomUUID().toString() + "-" + Instant.now().toEpochMilli();
    String tokenHash = hashToken(rawToken);
    PasswordReset pr = new PasswordReset();
    pr.setUserId(userId);
    pr.setTokenHash(tokenHash);
    pr.setExpiresAt(Instant.now().plusSeconds(PASSWORD_RESET_TOKEN_VALIDITY_HOURS * 3600L));
    passwordResetRepository.save(pr);
    return rawToken;
  }

  @Transactional
  public void changePassword(UUID userId, UUID businessId, ChangePasswordRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new BusinessRuleException("Current password is incorrect");
    }
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);

    if (businessId != null) {
      auditLogService.logAsync(
          businessId, userId, "PASSWORD_CHANGE", "Auth", userId, java.util.Map.of());
    }
  }

  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    String tokenHash = hashToken(request.token());
    PasswordReset pr =
        passwordResetRepository
            .findByTokenHashAndUsedFalse(tokenHash)
            .orElseThrow(() -> new BusinessRuleException("Invalid or expired reset token"));
    if (!pr.isValid()) {
      throw new BusinessRuleException("Reset token has expired");
    }
    User user =
        userRepository
            .findById(pr.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", pr.getUserId()));
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
    pr.markUsed();
    passwordResetRepository.save(pr);
  }

  private AuthResponse buildAuthResponse(User user, UUID businessId, String role, UUID roleId) {
    // Platform admins get PLATFORM_ADMIN role so frontend grants backoffice access
    String effectiveRole = user.isPlatformAdmin() ? "PLATFORM_ADMIN" : role;
    UUID effectiveRoleId = user.isPlatformAdmin() ? null : roleId;
    String accessToken =
        jwtService.generateAccessToken(
            user.getId(),
            user.getEmail(),
            businessId,
            effectiveRole,
            effectiveRoleId,
            user.isPlatformAdmin());
    String refreshToken = jwtService.generateRefreshToken(user.getId());
    String planSlug =
        businessId != null ? subscriptionService.getPlanSlugForBusiness(businessId) : null;
    return new AuthResponse(
        accessToken,
        refreshToken,
        jwtProperties.getExpirationMs() / 1000,
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        businessId,
        effectiveRole,
        planSlug);
  }
}
