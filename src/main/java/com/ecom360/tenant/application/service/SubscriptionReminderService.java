package com.ecom360.tenant.application.service;

import com.ecom360.identity.application.service.CachedRolePermissions;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.notification.application.service.NotificationPreferenceService;
import com.ecom360.notification.application.service.NotificationService;
import com.ecom360.notification.application.service.NotificationTypes;
import com.ecom360.shared.infrastructure.mail.EmailService;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.tenant.domain.repository.SubscriptionReminderSentRepository;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends in-app + email reminders before subscription / trial period end (idempotent per period).
 */
@Service
public class SubscriptionReminderService {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionReminderService.class);
  private static final DateTimeFormatter DATE_FR =
      DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(Locale.FRANCE);

  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionReminderSentRepository reminderSentRepository;
  private final BusinessUserRepository businessUserRepository;
  private final CachedRolePermissions cachedRolePermissions;
  private final NotificationService notificationService;
  private final NotificationPreferenceService notificationPreferenceService;
  private final UserRepository userRepository;
  private final EmailService emailService;
  private final List<Integer> reminderOffsetDays;

  public SubscriptionReminderService(
      SubscriptionRepository subscriptionRepository,
      SubscriptionReminderSentRepository reminderSentRepository,
      BusinessUserRepository businessUserRepository,
      CachedRolePermissions cachedRolePermissions,
      NotificationService notificationService,
      NotificationPreferenceService notificationPreferenceService,
      UserRepository userRepository,
      EmailService emailService,
      @Value("${app.subscription.reminder-offset-days:7,3,1}") String reminderOffsetsCsv) {
    this.subscriptionRepository = subscriptionRepository;
    this.reminderSentRepository = reminderSentRepository;
    this.businessUserRepository = businessUserRepository;
    this.cachedRolePermissions = cachedRolePermissions;
    this.notificationService = notificationService;
    this.notificationPreferenceService = notificationPreferenceService;
    this.userRepository = userRepository;
    this.emailService = emailService;
    this.reminderOffsetDays = parseOffsets(reminderOffsetsCsv);
  }

  private static List<Integer> parseOffsets(String csv) {
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(Integer::parseInt)
        .filter(i -> i > 0)
        .toList();
  }

  @Transactional
  public void sendRemindersFor(LocalDate today) {
    for (Integer offset : reminderOffsetDays) {
      if (offset == null || offset <= 0) {
        continue;
      }
      LocalDate periodEnd = today.plusDays(offset);
      List<Subscription> subs = subscriptionRepository.findTrialingOrActiveWithPeriodEnd(periodEnd);
      String kind = "period_end_minus_" + offset;
      for (Subscription sub : subs) {
        processOneSubscription(sub, kind, offset, today);
      }
    }
  }

  private void processOneSubscription(
      Subscription sub, String kind, int daysLeft, LocalDate today) {
    UUID subId = sub.getId();
    LocalDate periodEnd = sub.getCurrentPeriodEnd();
    if (reminderSentRepository.existsBySubscriptionIdAndReminderKindAndPeriodEnd(
        subId, kind, periodEnd)) {
      return;
    }

    boolean trialing = sub.isTrialing();
    boolean cancelling = sub.getCancelAtPeriodEnd();
    String subscriptionUrl = emailService.buildSubscriptionSettingsLink();

    for (BusinessUser bu :
        businessUserRepository.findByBusinessIdAndIsActive(sub.getBusinessId(), true)) {
      if (!cachedRolePermissions
          .codesForRole(bu.getBusinessRole().getId())
          .contains(Permission.SUBSCRIPTION_READ.name())) {
        continue;
      }

      notificationService.createNotification(
          sub.getBusinessId(),
          bu.getUserId(),
          NotificationTypes.SUBSCRIPTION,
          buildTitle(trialing, cancelling, daysLeft, periodEnd),
          buildBody(trialing, cancelling, daysLeft, periodEnd),
          subscriptionUrl);

      if (notificationPreferenceService.isEnabled(bu.getUserId(), NotificationTypes.SUBSCRIPTION)) {
        userRepository
            .findById(bu.getUserId())
            .filter(User::isActive)
            .ifPresent(
                u ->
                    emailService.sendSubscriptionPeriodReminderEmail(
                        u.getEmail(),
                        u.getFullName(),
                        trialing,
                        cancelling,
                        daysLeft,
                        periodEnd.format(DATE_FR),
                        subscriptionUrl));
      }
    }

    reminderSentRepository.save(
        com.ecom360.tenant.domain.model.SubscriptionReminderSent.record(subId, kind, periodEnd));
    log.debug(
        "Recorded subscription reminder kind={} subId={} periodEnd={}", kind, subId, periodEnd);
  }

  private static String buildTitle(boolean trialing, boolean cancelling, int days, LocalDate end) {
    String endStr = end.format(DATE_FR);
    if (cancelling) {
      return "Fin d’accès le " + endStr + " (résiliation)";
    }
    if (trialing) {
      if (days == 1) {
        return "Votre essai se termine demain (" + endStr + ")";
      }
      return "Il reste " + days + " jours d’essai (fin le " + endStr + ")";
    }
    if (days == 1) {
      return "Fin de période demain (" + endStr + ")";
    }
    return "Fin de période dans " + days + " jours (" + endStr + ")";
  }

  private static String buildBody(boolean trialing, boolean cancelling, int days, LocalDate end) {
    String endStr = end.format(DATE_FR);
    if (cancelling) {
      return "Selon votre demande, l’accès à 360 PME Commerce prendra fin le "
          + endStr
          + ". Vous pouvez réactiver un abonnement depuis les réglages.";
    }
    if (trialing) {
      return "Choisissez un plan avant le "
          + endStr
          + " pour continuer sans interruption. Ouvrez Réglages → Abonnement.";
    }
    return "La période en cours se termine le "
        + endStr
        + ". Vérifiez votre abonnement dans Réglages → Abonnement.";
  }
}
