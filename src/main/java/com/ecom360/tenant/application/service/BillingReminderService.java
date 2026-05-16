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
import com.ecom360.tenant.domain.model.Invoice;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.model.SubscriptionReminderSent;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.tenant.domain.repository.InvoiceReminderSentRepository;
import com.ecom360.tenant.domain.repository.InvoiceRepository;
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
 * Optional reminders for unpaid invoices and {@code past_due} grace end. Enable via {@code
 * app.subscription.billing-reminders-enabled=true}.
 */
@Service
public class BillingReminderService {

  private static final Logger log = LoggerFactory.getLogger(BillingReminderService.class);
  private static final DateTimeFormatter DATE_FR =
      DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(Locale.FRANCE);

  private final InvoiceRepository invoiceRepository;
  private final InvoiceReminderSentRepository invoiceReminderSentRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionReminderSentRepository subscriptionReminderSentRepository;
  private final BusinessUserRepository businessUserRepository;
  private final CachedRolePermissions cachedRolePermissions;
  private final NotificationService notificationService;
  private final NotificationPreferenceService notificationPreferenceService;
  private final UserRepository userRepository;
  private final EmailService emailService;
  private final List<Integer> reminderOffsetDays;
  private final boolean billingRemindersEnabled;

  public BillingReminderService(
      InvoiceRepository invoiceRepository,
      InvoiceReminderSentRepository invoiceReminderSentRepository,
      SubscriptionRepository subscriptionRepository,
      SubscriptionReminderSentRepository subscriptionReminderSentRepository,
      BusinessUserRepository businessUserRepository,
      CachedRolePermissions cachedRolePermissions,
      NotificationService notificationService,
      NotificationPreferenceService notificationPreferenceService,
      UserRepository userRepository,
      EmailService emailService,
      @Value("${app.subscription.reminder-offset-days:7,3,1}") String reminderOffsetsCsv,
      @Value("${app.subscription.billing-reminders-enabled:false}")
          boolean billingRemindersEnabled) {
    this.invoiceRepository = invoiceRepository;
    this.invoiceReminderSentRepository = invoiceReminderSentRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.subscriptionReminderSentRepository = subscriptionReminderSentRepository;
    this.businessUserRepository = businessUserRepository;
    this.cachedRolePermissions = cachedRolePermissions;
    this.notificationService = notificationService;
    this.notificationPreferenceService = notificationPreferenceService;
    this.userRepository = userRepository;
    this.emailService = emailService;
    this.reminderOffsetDays = parseOffsets(reminderOffsetsCsv);
    this.billingRemindersEnabled = billingRemindersEnabled;
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
  public void sendBillingRemindersFor(LocalDate today) {
    if (!billingRemindersEnabled) {
      return;
    }
    for (Integer offset : reminderOffsetDays) {
      if (offset == null || offset <= 0) {
        continue;
      }
      LocalDate target = today.plusDays(offset);
      remindUnpaidInvoicesDueOn(target, offset);
      remindPastDueGraceEndingOn(target, offset);
    }
  }

  private void remindUnpaidInvoicesDueOn(LocalDate dueDate, int offset) {
    String kind = "invoice_due_in_" + offset + "_days";
    for (Invoice inv : invoiceRepository.findUnpaidWithDueDate(dueDate)) {
      if (invoiceReminderSentRepository.existsByInvoiceIdAndReminderKindAndDueDate(
          inv.getId(), kind, inv.getDueDate())) {
        continue;
      }
      String url = emailService.buildSubscriptionSettingsLink();
      String title =
          "Facturation — échéance dans "
              + offset
              + (offset == 1 ? " jour" : " jours")
              + " ("
              + inv.getNumber()
              + ")";
      String body =
          "Une facture d’un montant de "
              + inv.getAmount()
              + " FCFA doit être réglée au plus tard le "
              + inv.getDueDate().format(DATE_FR)
              + ".";

      notifyBillingRecipients(inv.getBusinessId(), title, body, url);

      invoiceReminderSentRepository.save(
          com.ecom360.tenant.domain.model.InvoiceReminderSent.record(
              inv.getId(), kind, inv.getDueDate()));
      log.debug("Recorded invoice billing reminder {}", inv.getId());
    }
  }

  private void remindPastDueGraceEndingOn(LocalDate graceEndDate, int offset) {
    String kind = "grace_end_minus_" + offset;
    for (Subscription sub : subscriptionRepository.findPastDueWithGraceEndingOn(graceEndDate)) {
      LocalDate g = sub.getGracePeriodEndsAt();
      if (g == null) {
        continue;
      }
      if (subscriptionReminderSentRepository.existsBySubscriptionIdAndReminderKindAndPeriodEnd(
          sub.getId(), kind, g)) {
        continue;
      }

      String url = emailService.buildSubscriptionSettingsLink();
      String title =
          "Abonnement en retard — fin de tolérance dans "
              + offset
              + (offset == 1 ? " jour" : " jours")
              + " ("
              + g.format(DATE_FR)
              + ")";
      String body =
          "Régularisez votre paiement avant le "
              + g.format(DATE_FR)
              + " pour conserver l’accès à 360 PME Commerce.";
      notifyBillingRecipients(sub.getBusinessId(), title, body, url);

      subscriptionReminderSentRepository.save(
          SubscriptionReminderSent.record(sub.getId(), kind, g));
      log.debug("Recorded grace billing reminder for subscription {}", sub.getId());
    }
  }

  private void notifyBillingRecipients(
      UUID businessId, String title, String body, String actionUrl) {
    for (BusinessUser bu : businessUserRepository.findByBusinessIdAndIsActive(businessId, true)) {
      if (!cachedRolePermissions
          .codesForRole(bu.getBusinessRole().getId())
          .contains(Permission.SUBSCRIPTION_READ.name())) {
        continue;
      }
      notificationService.createNotification(
          businessId, bu.getUserId(), NotificationTypes.BILLING, title, body, actionUrl);
      if (notificationPreferenceService.isEnabled(bu.getUserId(), NotificationTypes.BILLING)) {
        userRepository
            .findById(bu.getUserId())
            .filter(User::isActive)
            .ifPresent(
                u ->
                    emailService.sendBillingReminderEmail(
                        u.getEmail(), u.getFullName(), title, body + "\n\n" + actionUrl));
      }
    }
  }
}
