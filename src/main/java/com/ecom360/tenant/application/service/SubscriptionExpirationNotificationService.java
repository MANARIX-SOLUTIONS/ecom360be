package com.ecom360.tenant.application.service;

import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.notification.application.service.NotificationPublisher;
import com.ecom360.notification.application.service.NotificationTypes;
import com.ecom360.shared.infrastructure.mail.EmailService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** In-app + email when a trial or paid period actually expires. */
@Service
public class SubscriptionExpirationNotificationService {

  private static final Logger log =
      LoggerFactory.getLogger(SubscriptionExpirationNotificationService.class);
  private static final DateTimeFormatter DATE_FR =
      DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(Locale.FRANCE);

  private final NotificationPublisher notificationPublisher;
  private final UserRepository userRepository;
  private final EmailService emailService;

  public SubscriptionExpirationNotificationService(
      NotificationPublisher notificationPublisher,
      UserRepository userRepository,
      EmailService emailService) {
    this.notificationPublisher = notificationPublisher;
    this.userRepository = userRepository;
    this.emailService = emailService;
  }

  public void notifyExpired(UUID businessId, boolean wasTrialing, LocalDate periodEnd) {
    try {
      notifyExpiredInternal(businessId, wasTrialing, periodEnd);
    } catch (Exception e) {
      log.warn(
          "Expiration notification skipped for business {}: {}",
          businessId,
          e.getMessage());
    }
  }

  private void notifyExpiredInternal(
      UUID businessId, boolean wasTrialing, LocalDate periodEnd) {
    String endStr = periodEnd != null ? periodEnd.format(DATE_FR) : "";
    String title =
        wasTrialing ? "Votre essai est terminé" : "Votre abonnement a expiré";
    String body =
        wasTrialing
            ? ("L’essai gratuit"
                + (endStr.isEmpty() ? "" : " (fin le " + endStr + ")")
                + " est clos. Choisissez un plan dans Réglages → Abonnement.")
            : ("La période"
                + (endStr.isEmpty() ? "" : " close le " + endStr)
                + " est terminée. Renouvelez depuis Réglages → Abonnement.");
    String actionUrl = "/settings/subscription";
    String subscriptionUrl = emailService.buildSubscriptionSettingsLink();

    for (UUID userId :
        notificationPublisher.notifyBusiness(
            businessId,
            NotificationTypes.SUBSCRIPTION,
            title,
            body,
            actionUrl,
            Permission.SUBSCRIPTION_READ)) {
      userRepository
          .findById(userId)
          .filter(User::isActive)
          .ifPresent(
              u ->
                  emailService.sendSubscriptionExpiredEmail(
                      u.getEmail(), u.getFullName(), wasTrialing, subscriptionUrl));
    }
  }
}
