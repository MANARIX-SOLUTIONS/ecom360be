package com.ecom360.tenant.application.dto;

/** Request to cancel subscription. Default: cancel at period end (keep access until then). */
public record CancelSubscriptionRequest(Boolean atPeriodEnd) {

  public CancelSubscriptionRequest {
    if (atPeriodEnd == null) {
      atPeriodEnd = true;
    }
  }

  public Boolean atPeriodEnd() {
    return atPeriodEnd != null && atPeriodEnd;
  }
}
