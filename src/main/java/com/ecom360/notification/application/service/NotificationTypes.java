package com.ecom360.notification.application.service;

import java.util.Set;

/** Supported notification types. */
public final class NotificationTypes {

  public static final String SUBSCRIPTION = "subscription";
  public static final String SYSTEM = "system";
  public static final String BILLING = "billing";
  public static final String SALE = "sale";

  public static final Set<String> ALL = Set.of("low_stock", "payment_received", SUBSCRIPTION, SYSTEM, BILLING, SALE);

  private NotificationTypes() {
  }
}
