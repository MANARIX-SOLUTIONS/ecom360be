package com.ecom360.tenant.application.dto;

/** Current usage vs plan limits for the business. */
public record SubscriptionUsageResponse(
    int usersCount,
    int usersLimit,
    int storesCount,
    int storesLimit,
    int productsCount,
    int productsLimit,
    int clientsCount,
    int clientsLimit,
    int suppliersCount,
    int suppliersLimit,
    long salesThisMonth,
    int salesLimit) {
  /** 0 = unlimited */
  public boolean isUsersUnlimited() {
    return usersLimit == 0;
  }

  public boolean isStoresUnlimited() {
    return storesLimit == 0;
  }

  public boolean isProductsUnlimited() {
    return productsLimit == 0;
  }

  public boolean isClientsUnlimited() {
    return clientsLimit == 0;
  }

  public boolean isSuppliersUnlimited() {
    return suppliersLimit == 0;
  }

  public boolean isSalesUnlimited() {
    return salesLimit == 0;
  }
}
