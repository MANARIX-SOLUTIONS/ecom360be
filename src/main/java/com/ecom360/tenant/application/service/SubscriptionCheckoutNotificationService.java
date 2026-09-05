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

/**
 * In-app + email for subscription activation (today: changePlan / admin) and
 * checkout failure (call from PSP intent → failed when T1 checkout exists).
 */
@Service
public class SubscriptionCheckoutNotificationService {

  private static final Logger log =
      LoggerFactory.getLogger(SubscriptionCheckoutNotificationService.class);
  private static final DateTimeFormatter DATE_FR =
      DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(Locale.FRANCE);

  private final NotificationPublisher notificationPublisher;
  private final UserRepository userRepository;
  private final EmailService emailService;

  public SubscriptionCheckoutNotificationService(
      NotificationPublisher notificationPublisher,
      UserRepository userRepository,
      EmailService emailService) {
    this.notificationPublisher = notificationPublisher;
    this.userRepository = userRepository;
    this.emailService = emailService;
  }

  public void notifyPaid(
      UUID businessId, String planName, String billingCycle, LocalDate periodEnd) {
    try {
      notifyPaidInternal(businessId, planName, billingCycle, periodEnd);
    } catch (Exception e) {
      log.warn(
          "Checkout paid notification skipped for business {}: {}",
          businessId,
          e.getMessage());
    }
  }

  public void notifyFailed(UUID businessId, String reason) {
    try {
      notifyFailedInternal(businessId, reason);
    } catch (Exception e) {
      log.warn(
          "Checkout failed notification skipped for business {}: {}",
          businessId,
          e.getMessage());
    }
  }

  private void notifyPaidInternal(
      UUID businessId, String planName, String billingCycle, LocalDate periodEnd) {
    String name = planName != null && !planName.isBlank() ? planName : "votre plan";
    String cycleLabel = cycleLabel(billingCycle);
    String endStr = periodEnd != null ? periodEnd.format(DATE_FR) : "";
    String title = "Abonnement activé — " + name;
    String body =
        "Le plan "
            + name
            + " ("
            + cycleLabel
            + ") est actif"
            + (endStr.isEmpty() ? "." : " jusqu’au " + endStr + ".");
    String actionUrl = "/settings/subscription";
    String mailUrl = emailService.buildSubscriptionSettingsLink();

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
                  emailService.sendSubscriptionCheckoutPaidEmail(
                      u.getEmail(),
                      u.getFullName(),
                      name,
                      cycleLabel,
                      endStr.isEmpty() ? "—" : endStr,
                      mailUrl));
    }
  }

  private void notifyFailedInternal(UUID businessId, String reason) {
    String body =
        reason != null && !reason.isBlank()
            ? reason.strip()
            : "Le paiement n’a pas abouti. Réessayez depuis Réglages → Abonnement.";
    String actionUrl = "/settings/subscription";
    String mailUrl = emailService.buildSubscriptionSettingsLink();

    for (UUID userId :
        notificationPublisher.notifyBusiness(
            businessId,
            NotificationTypes.SUBSCRIPTION,
            "Paiement d’abonnement échoué",
            body,
            actionUrl,
            Permission.SUBSCRIPTION_READ)) {
      userRepository
          .findById(userId)
          .filter(User::isActive)
          .ifPresent(
              u ->
                  emailService.sendSubscriptionCheckoutFailedEmail(
                      u.getEmail(), u.getFullName(), body, mailUrl));
    }
  }

  private static String cycleLabel(String billingCycle) {
    return "yearly".equalsIgnoreCase(billingCycle) ? "annuel" : "mensuel";
  }
}
