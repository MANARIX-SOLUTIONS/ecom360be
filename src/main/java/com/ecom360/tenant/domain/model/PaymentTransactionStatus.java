package com.ecom360.tenant.domain.model;

public final class PaymentTransactionStatus {

  public static final String PENDING = "pending";
  public static final String PAID = "paid";
  public static final String FAILED = "failed";
  public static final String CANCELLED = "cancelled";
  public static final String EXPIRED = "expired";

  private PaymentTransactionStatus() {
  }
}
