package com.ecom360.tenant.payment.application.job;

import com.ecom360.tenant.payment.application.service.SubscriptionCheckoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expires abandoned subscription checkout intents (pending too long). */
@Component
public class SubscriptionPaymentIntentExpirationJob {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionPaymentIntentExpirationJob.class);

  private final SubscriptionCheckoutService checkoutService;

  public SubscriptionPaymentIntentExpirationJob(SubscriptionCheckoutService checkoutService) {
    this.checkoutService = checkoutService;
  }

  /** Every hour — expire pending intents older than TTL. */
  @Scheduled(cron = "${app.payment.intent-expiration-cron:0 20 * * * ?}")
  public void expireStaleIntents() {
    int n = checkoutService.expireStalePendingIntents();
    if (n > 0) {
      log.info("Expired {} stale subscription payment intent(s)", n);
    }
  }
}
