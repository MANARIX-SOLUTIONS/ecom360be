package com.ecom360.delivery.domain.model;

import java.util.Locale;

/**
 * Persisté dans {@code livraison.status} (CHECK: delivered, failed,
 * cancelled). Lowercase names match the DB column and the API payload.
 */
public enum DeliveryStatus {
  delivered,
  failed,
  cancelled;

  public static DeliveryStatus from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Statut de livraison requis.");
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (DeliveryStatus value : values()) {
      if (value.name().equals(normalized)) {
        return value;
      }
    }
    throw new IllegalArgumentException(
        "Statut de livraison invalide: " + raw);
  }
}
