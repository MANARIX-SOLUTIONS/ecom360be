package com.ecom360.tenant.application.service;

import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.notification.application.service.NotificationPreferenceService;
import com.ecom360.notification.application.service.NotificationService;
import com.ecom360.notification.application.service.NotificationTypes;
import com.ecom360.shared.infrastructure.mail.EmailService;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Welcome in-app notification + optional email after tenant provisioning with an owner user. */
@Service
public class TenantWelcomeNotificationService {

  private static final Logger log = LoggerFactory.getLogger(TenantWelcomeNotificationService.class);
  private static final DateTimeFormatter DATE_FR =
      DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(Locale.FRANCE);

  private final BusinessRepository businessRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;
  private final NotificationPreferenceService notificationPreferenceService;
  private final EmailService emailService;

  public TenantWelcomeNotificationService(
      BusinessRepository businessRepository,
      UserRepository userRepository,
      NotificationService notificationService,
      NotificationPreferenceService notificationPreferenceService,
      EmailService emailService) {
    this.businessRepository = businessRepository;
    this.userRepository = userRepository;
    this.notificationService = notificationService;
    this.notificationPreferenceService = notificationPreferenceService;
    this.emailService = emailService;
  }

  /** Best-effort: never throws to callers provisioning tenants. */
  public void sendWelcomeAfterProvisioning(UUID businessId, UUID ownerUserId) {
    try {
      sendWelcomeInternal(businessId, ownerUserId);
    } catch (Exception e) {
      log.warn(
          "Welcome notification skipped for business {} owner {}: {}",
          businessId,
          ownerUserId,
          e.getMessage());
    }
  }

  private void sendWelcomeInternal(UUID businessId, UUID ownerUserId) {
    User owner = userRepository.findById(ownerUserId).filter(User::isActive).orElse(null);
    if (owner == null) {
      return;
    }
    Business business = businessRepository.findById(businessId).orElse(null);
    if (business == null) {
      return;
    }

    String trialFmt = null;
    if (business.getTrialEndsAt() != null && business.isTrial()) {
      trialFmt = business.getTrialEndsAt().format(DATE_FR);
    }

    String subscriptionUrl = emailService.buildSubscriptionSettingsLink();
    String title = "Bienvenue sur 360 PME Commerce";
    String body =
        trialFmt != null
            ? ("Votre essai gratuit est ouvert jusqu’au "
                + trialFmt
                + ". Créez un magasin, ajoutez des produits — ou souscrivez un plan dans Réglages → Abonnement.")
            : "Votre compte est activé. Créez un magasin, ajoutez des produits — gérez l’offre depuis Réglages → Abonnement.";

    notificationService.createNotification(
        businessId, ownerUserId, NotificationTypes.SYSTEM, title, body, subscriptionUrl);

    if (!notificationPreferenceService.isEnabled(ownerUserId, NotificationTypes.SYSTEM)) {
      return;
    }
    emailService.sendWelcomeAfterSignupEmail(
        owner.getEmail(),
        owner.getFullName(),
        business.getName(),
        trialFmt,
        emailService.buildAppHomeLink());
  }
}
