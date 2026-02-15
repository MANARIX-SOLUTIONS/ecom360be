package com.ecom360.tenant.application.service;

import com.ecom360.identity.application.service.AuthService;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.shared.infrastructure.mail.EmailService;
import com.ecom360.tenant.application.dto.BusinessUserResponse;
import com.ecom360.tenant.application.dto.InviteUserRequest;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class BusinessUserService {

    private static final int TEMP_PASSWORD_LENGTH = 24;

    private final BusinessUserRepository businessUserRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionService subscriptionService;
    private final AuthService authService;
    private final EmailService emailService;

    public BusinessUserService(BusinessUserRepository businessUserRepository,
                              UserRepository userRepository,
                              BusinessRepository businessRepository,
                              PasswordEncoder passwordEncoder,
                              SubscriptionService subscriptionService,
                              AuthService authService,
                              EmailService emailService) {
        this.businessUserRepository = businessUserRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.passwordEncoder = passwordEncoder;
        this.subscriptionService = subscriptionService;
        this.authService = authService;
        this.emailService = emailService;
    }

    public List<BusinessUserResponse> list(UserPrincipal p) {
        requireBiz(p);
        return businessUserRepository.findByBusinessIdAndIsActive(p.businessId(), true).stream()
                .map(bu -> {
                    User u = userRepository.findById(bu.getUserId())
                            .orElseThrow(() -> new ResourceNotFoundException("User", bu.getUserId()));
                    return new BusinessUserResponse(bu.getId(), bu.getUserId(), u.getFullName(), u.getEmail(), bu.getRole(), bu.getIsActive());
                })
                .toList();
    }

    @Transactional
    public BusinessUserResponse invite(InviteUserRequest req, UserPrincipal p) {
        requireBiz(p);
        subscriptionService.getPlanForBusiness(p.businessId()).ifPresent(plan -> {
            if (!plan.isUnlimited(plan.getMaxUsers())) {
                long count = businessUserRepository.findByBusinessIdAndIsActive(p.businessId(), true).size();
                if (count >= plan.getMaxUsers()) {
                    throw new BusinessRuleException("Limite du plan atteinte : maximum " + plan.getMaxUsers() + " utilisateur(s). Passez à un plan supérieur.");
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
            emailService.sendInvitationEmail(user.getEmail(), user.getFullName(), businessName, setPasswordLink);
        }
        if (businessUserRepository.existsByBusinessIdAndUserId(p.businessId(), user.getId())) {
            throw new ResourceAlreadyExistsException("User", req.email() + " is already a member");
        }
        BusinessUser bu = BusinessUser.create(p.businessId(), user.getId(), req.role().toLowerCase());
        bu = businessUserRepository.save(bu);
        return new BusinessUserResponse(bu.getId(), bu.getUserId(), user.getFullName(), user.getEmail(), bu.getRole(), bu.getIsActive());
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
        return Base64.getEncoder().encodeToString(bytes).replaceAll("[^A-Za-z0-9]", "").substring(0, TEMP_PASSWORD_LENGTH);
    }
}
