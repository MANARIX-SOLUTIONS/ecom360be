package com.ecom360.sales.application.job;

import com.ecom360.sales.application.service.PosDigitalCheckoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases the stock of POS Wave / Orange Money sales left unpaid past their TTL. */
@Component
public class SalePaymentIntentExpirationJob {

  private static final Logger log = LoggerFactory.getLogger(SalePaymentIntentExpirationJob.class);

  private final PosDigitalCheckoutService checkoutService;

  public SalePaymentIntentExpirationJob(PosDigitalCheckoutService checkoutService) {
    this.checkoutService = checkoutService;
  }

  @Scheduled(cron = "${app.payment.pos-intent-expiration-cron:0 */5 * * * ?}")
  public void expireStaleIntents() {
    int n = checkoutService.expireStalePendingIntents();
    if (n > 0) {
      log.info("Expired {} stale POS payment intent(s)", n);
    }
  }
}
