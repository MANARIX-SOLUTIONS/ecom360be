package com.ecom360.identity.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Règles d'accès aux écrans de navigation : chaque clé correspond à une entrée de menu / route ;
 * l'utilisateur doit posséder au moins une des permissions listées. Doit rester aligné avec le
 * client (fallback) jusqu'à chargement de {@code /permissions/me}.
 */
public final class NavigationPermissionRules {

  private NavigationPermissionRules() {}

  public static Map<String, List<String>> asMap() {
    Map<String, List<String>> m = new LinkedHashMap<>();
    m.put("dashboard", List.of("SALES_READ", "PRODUCTS_READ"));
    m.put("pos", List.of("SALES_CREATE"));
    m.put("products", List.of("PRODUCTS_READ"));
    m.put("clients", List.of("CLIENTS_READ"));
    m.put("suppliers", List.of("SUPPLIERS_READ"));
    /** Au moins une permission livreurs (souvent READ seul n’est pas coché si l’admin donne Créer, etc.). */
    m.put(
        "livreurs",
        List.of(
            "DELIVERY_COURIERS_READ",
            "DELIVERY_COURIERS_CREATE",
            "DELIVERY_COURIERS_UPDATE",
            "DELIVERY_COURIERS_DELETE"));
    m.put("globalView", List.of("GLOBAL_VIEW_READ"));
    m.put("expenses", List.of("EXPENSES_READ"));
    /** Rapports / exports : permission dédiée (distinct du tableau de bord). */
    m.put("reports", List.of("REPORTS_READ"));
    m.put(
        "settings",
        List.of("STORES_READ", "SUBSCRIPTION_READ", "BUSINESS_USERS_READ"));
    m.put("settings:stores", List.of("STORES_READ"));
    m.put("settings:profile", List.of("STORES_READ"));
    m.put("settings:subscription", List.of("SUBSCRIPTION_READ"));
    m.put("settings:users", List.of("BUSINESS_USERS_READ"));
    m.put("settings:roles", List.of("BUSINESS_USERS_READ"));
    m.put("settings:security", List.of("STORES_READ"));
    m.put("settings:notifications", List.of("STORES_READ"));
    m.put("backoffice", List.of());
    return Collections.unmodifiableMap(m);
  }
}
