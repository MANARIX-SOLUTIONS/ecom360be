package com.ecom360.tenant.application.job;

import com.ecom360.tenant.application.service.SubscriptionReminderService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends subscription / trial expiry reminders before {@link SubscriptionExpirationJob} expires
 * access.
 */
@Component
public class SubscriptionReminderJob {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionReminderJob.class);

  private final SubscriptionReminderService reminderService;

  public SubscriptionReminderJob(SubscriptionReminderService reminderService) {
    this.reminderService = reminderService;
  }

  /** Default daily 08:00 — offsets vs {@code app.subscription.reminder-offset-days}. */
  @Scheduled(cron = "${app.subscription.reminder-cron:0 0 8 * * ?}")
  public void runReminders() {
    LocalDate today = LocalDate.now();
    reminderService.sendRemindersFor(today);
    log.debug("Subscription reminders processed for {}", today);
  }
}
