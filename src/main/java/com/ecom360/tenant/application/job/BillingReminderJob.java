package com.ecom360.tenant.application.job;

import com.ecom360.tenant.application.service.BillingReminderService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Invoice / grace-period reminders when {@code app.subscription.billing-reminders-enabled=true}.
 */
@Component
public class BillingReminderJob {

  private static final Logger log = LoggerFactory.getLogger(BillingReminderJob.class);

  private final BillingReminderService billingReminderService;

  public BillingReminderJob(BillingReminderService billingReminderService) {
    this.billingReminderService = billingReminderService;
  }

  /** Same slot as subscription reminders (+5 min offset) — no-op if billing reminders disabled. */
  @Scheduled(cron = "${app.subscription.billing-reminder-cron:0 5 8 * * ?}")
  public void run() {
    LocalDate today = LocalDate.now();
    billingReminderService.sendBillingRemindersFor(today);
    log.trace("Billing reminder job ran for {}", today);
  }
}
