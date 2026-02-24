package com.ecom360.notification.application.service;

import java.util.Set; /** Supported notification types. */
public final class NotificationTypes {
  public static final Set<String> ALL =
      Set.of("low_stock", "payment_received", "subscription", "system");

  private NotificationTypes() {}
}
