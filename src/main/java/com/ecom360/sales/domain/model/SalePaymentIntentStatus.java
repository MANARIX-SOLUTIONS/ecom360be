package com.ecom360.sales.domain.model;

public final class SalePaymentIntentStatus {

  public static final String PENDING = "pending";
  public static final String PAID = "paid";
  public static final String FAILED = "failed";
  public static final String EXPIRED = "expired";
  public static final String CANCELLED = "cancelled";
  /** Cashier confirmed the payment by hand (PSP unavailable). */
  public static final String MANUAL = "manual";

  private SalePaymentIntentStatus() {}
}
