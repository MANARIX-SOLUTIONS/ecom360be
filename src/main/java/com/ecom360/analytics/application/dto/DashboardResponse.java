package com.ecom360.analytics.application.dto;

import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        long todaySalesCount,
        long todayRevenue,
        /**
         * Dépenses enregistrées pour la date du jour (filtrage par date de dépense).
         */
        long todayExpenses,
        long periodSalesCount,
        long periodRevenue,
        long periodExpenses,
        long periodProfit,
        long totalProducts,
        long totalClients,
        long totalSuppliers,
        long totalStores,
        List<LowStockItem> lowStockItems,
        List<RecentSale> recentSales,
        List<TopProduct> topProducts,
        boolean analyticsLimitedToToday,
        Long periodGrossMargin,
        List<TopMarginProduct> topMarginProducts,
        /**
         * Nombre total de produits (période) — les {@code topProducts} ne contiennent
         * qu’un aperçu.
         */
        long topProductsTotal,
        /**
         * Nombre total de lignes stock faible — les {@code lowStockItems} ne
         * contiennent qu’un aperçu.
         */
        long lowStockItemsTotal,
        /**
         * ISO-8601 : date de création du commerce (onboarding dashboard max 2 jours
         * côté client).
         */
        String businessCreatedAt,
        /** ISO yyyy-MM-dd : borne début effective de la période agrégée (inclus). */
        String periodStart,
        /** ISO yyyy-MM-dd : borne fin effective de la période agrégée (inclus). */
        String periodEnd,
        /**
         * Même durée que la période courante, immédiatement avant {@code periodStart}.
         */
        long previousPeriodRevenue,
        long previousPeriodSalesCount,
        long previousPeriodExpenses,
        long previousPeriodProfit,
        /** Clients actifs avec solde crédit &gt; 0. */
        long debtorClientsCount,
        /** Somme des soldes crédit &gt; 0 (FCFA). */
        long totalReceivable) {
    public record LowStockItem(
            UUID productId, String productName, String storeName, int quantity, int minStock) {
    }

    public record RecentSale(
            java.util.UUID saleId,
            String receiptNumber,
            int total,
            String paymentMethod,
            String status,
            String createdAt) {
    }

    public record TopProduct(
            UUID productId, String productName, long totalQuantity, long totalRevenue) {
    }

    public record TopMarginProduct(UUID productId, String productName, long marginAmount) {
    }
}
