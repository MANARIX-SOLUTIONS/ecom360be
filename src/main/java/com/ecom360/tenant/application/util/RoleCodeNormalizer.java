package com.ecom360.tenant.application.util;

/** Normalise les codes de rôle (synonymes anglais / anciens codes vers PROPRIETAIRE / …). */
public final class RoleCodeNormalizer {

  private RoleCodeNormalizer() {}

  public static String toBusinessRoleCode(String raw) {
    if (raw == null || raw.isBlank()) {
      return "CAISSIER";
    }
    String r = raw.trim();
    String upper = r.toUpperCase();
    return switch (upper) {
      case "PROPRIETAIRE", "PROPRIÉTAIRE", "ADMIN" -> "PROPRIETAIRE";
      case "GESTIONNAIRE", "MANAGER" -> "GESTIONNAIRE";
      case "CAISSIER", "SELLER" -> "CAISSIER";
      default -> upper;
    };
  }
}
