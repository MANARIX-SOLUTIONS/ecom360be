package com.ecom360.tenant.payment.domain.model;

public final class SubscriptionPaymentStatus {

  public static final String PENDING = "pending";
  public static final String PAID = "paid";
  public static final String FAILED = "failed";
  public static final String EXPIRED = "expired";
  public static final String CANCELLED = "cancelled";

  private SubscriptionPaymentStatus() {
  }
}
