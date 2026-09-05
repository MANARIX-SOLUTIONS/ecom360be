package com.ecom360.tenant.application.job;

import com.ecom360.tenant.application.service.SubscriptionExpirationNotificationService;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires trials and subscriptions past their period end. Syncs Business status when trial expires.
 * Handles cancel-at-period-end requests.
 */
@Component
public class SubscriptionExpirationJob {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionExpirationJob.class);

  private final SubscriptionRepository subscriptionRepository;
  private final BusinessRepository businessRepository;
  private final SubscriptionExpirationNotificationService expirationNotificationService;

  public SubscriptionExpirationJob(
      SubscriptionRepository subscriptionRepository,
      BusinessRepository businessRepository,
      SubscriptionExpirationNotificationService expirationNotificationService) {
    this.subscriptionRepository = subscriptionRepository;
    this.businessRepository = businessRepository;
    this.expirationNotificationService = expirationNotificationService;
  }

  /** Run daily at 02:00 — expire trials/subscriptions and process cancel-at-period-end. */
  @Scheduled(cron = "${app.subscription.expiration-cron:0 0 2 * * ?}")
  @Transactional
  public void expireSubscriptions() {
    LocalDate today = LocalDate.now();

    List<Subscription> toExpire = subscriptionRepository.findExpiredTrialsAndSubscriptions(today);
    for (Subscription sub : toExpire) {
      boolean wasTrialing = sub.isTrialing();
      LocalDate periodEnd = sub.getCurrentPeriodEnd();
      sub.expire();
      subscriptionRepository.save(sub);
      syncBusinessStatusOnExpiration(sub.getBusinessId(), wasTrialing);
      expirationNotificationService.notifyExpired(
          sub.getBusinessId(), wasTrialing, periodEnd);
      log.info(
          "Expired subscription {} (business={}, wasTrialing={})",
          sub.getId(),
          sub.getBusinessId(),
          wasTrialing);
    }

    List<Subscription> toCancel = subscriptionRepository.findCancelledAtPeriodEnd(today);
    for (Subscription sub : toCancel) {
      sub.markCancelled();
      subscriptionRepository.save(sub);
      log.info(
          "Cancelled at period end: subscription {} (business={})",
          sub.getId(),
          sub.getBusinessId());
    }
  }

  private void syncBusinessStatusOnExpiration(java.util.UUID businessId, boolean wasTrialing) {
    businessRepository
        .findById(businessId)
        .ifPresent(
            biz -> {
              if ("trial".equals(biz.getStatus()) || "active".equals(biz.getStatus())) {
                biz.setStatus("expired");
                if (wasTrialing) {
                  biz.setTrialUsedAt(Instant.now());
                }
                businessRepository.save(biz);
                log.debug(
                    "Set business {} status to expired (trial/subscription ended)", businessId);
              }
            });
  }
}
