package com.ecom360.analytics.application.dto;

import java.util.List;
import java.util.UUID;

public record DashboardResponse(
    long todaySalesCount, long todayRevenue, long periodSalesCount, long periodRevenue,
    long periodExpenses, long periodProfit, long totalProducts, long totalClients,
    long totalSuppliers, long totalStores, List<LowStockItem> lowStockItems,
    List<RecentSale> recentSales, List<TopProduct> topProducts
) {
    public record LowStockItem(UUID productId, String productName, String storeName, int quantity, int minStock) {}
    public record RecentSale(java.util.UUID saleId, String receiptNumber, int total, String paymentMethod, String createdAt) {}
    public record TopProduct(UUID productId, String productName, long totalQuantity, long totalRevenue) {}
}
