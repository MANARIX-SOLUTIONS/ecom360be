package com.ecom360.tenant.domain.model;

/** Subscription lifecycle statuses — aligned with real-world SaaS platforms. */
public final class SubscriptionStatus {

  /** Trial period active. */
  public static final String TRIALING = "trialing";

  /** Paid subscription active. */
  public static final String ACTIVE = "active";

  /** Period ended (trial or paid) — no access. */
  public static final String EXPIRED = "expired";

  /** Payment failed — grace period before suspension. */
  public static final String PAST_DUE = "past_due";

  /** User or system cancelled. */
  public static final String CANCELLED = "cancelled";

  /** Subscription paused (e.g. user request). */
  public static final String PAUSED = "paused";

  /** Payment not completed (e.g. checkout abandoned). */
  public static final String INCOMPLETE = "incomplete";

  /** Statuses that grant access to the product. */
  public static final java.util.Set<String> ACCESS_GRANTING =
      java.util.Set.of(TRIALING, ACTIVE);

  /** Statuses that can transition to paid (subscribe). */
  public static final java.util.Set<String> CAN_SUBSCRIBE =
      java.util.Set.of(TRIALING, EXPIRED, CANCELLED, PAUSED);

  private SubscriptionStatus() {}
}
