package com.ecom360.analytics.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Vue globale de toutes les boutiques : KPIs agrégés + répartition par store. */
public record GlobalViewResponse(
    LocalDate periodStart,
    LocalDate periodEnd,
    long totalRevenue,
    long totalSalesCount,
    double averageBasket,
    long totalExpenses,
    long totalProfit,
    int storeCount,
    List<StoreStats> salesByStore,
    List<DashboardResponse.LowStockItem> lowStockItems,
    List<DashboardResponse.TopProduct> topProducts) {

  public record StoreStats(
      UUID storeId, String storeName, long revenue, long salesCount, double sharePercent) {}
}
