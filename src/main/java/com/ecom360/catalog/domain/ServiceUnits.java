package com.ecom360.catalog.domain;

import java.util.Locale;

/** Unité catalogue qui représente un service (prestation ou forfait). */
public final class ServiceUnits {

  public static final String PRESTATION = "prestation";
  public static final String FORFAIT = "forfait";

  private ServiceUnits() {}

  public static boolean isService(String unit) {
    if (unit == null) {
      return false;
    }
    String normalized = unit.trim().toLowerCase(Locale.ROOT);
    return PRESTATION.equals(normalized) || FORFAIT.equals(normalized);
  }
}
