package com.ecom360.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Garde l’alignement avec les clés de navigation du client (Permission dans roles.ts). */
class NavigationPermissionRulesTest {

  @Test
  void expectedKeysMatchFrontendNavContract() {
    Map<String, java.util.List<String>> m = NavigationPermissionRules.asMap();
    assertThat(m.keySet())
        .containsExactlyInAnyOrder(
            "dashboard",
            "pos",
            "products",
            "clients",
            "suppliers",
            "livreurs",
            "globalView",
            "expenses",
            "reports",
            "settings",
            "settings:stores",
            "settings:profile",
            "settings:subscription",
            "settings:users",
            "settings:roles",
            "settings:security",
            "settings:notifications",
            "backoffice");
  }

  @Test
  void nonBackofficeRoutesRequireAtLeastOnePermissionCode() {
    for (var e : NavigationPermissionRules.asMap().entrySet()) {
      if ("backoffice".equals(e.getKey())) {
        assertThat(e.getValue()).isEmpty();
      } else {
        assertThat(e.getValue()).isNotEmpty();
      }
    }
  }

  @Test
  void reportsRequireSalesReadOnly_dashboardAllowsSalesOrProductsRead() {
    var m = NavigationPermissionRules.asMap();
    assertThat(m.get("reports")).containsExactly("REPORTS_READ");
    assertThat(m.get("dashboard")).containsExactly("SALES_READ", "PRODUCTS_READ");
  }

  @Test
  void livreursAcceptsAnyDeliveryCourierPermission() {
    assertThat(NavigationPermissionRules.asMap().get("livreurs"))
        .containsExactlyInAnyOrder(
            "DELIVERY_COURIERS_READ",
            "DELIVERY_COURIERS_CREATE",
            "DELIVERY_COURIERS_UPDATE",
            "DELIVERY_COURIERS_DELETE");
  }
}
