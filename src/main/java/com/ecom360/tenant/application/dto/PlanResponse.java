package com.ecom360.tenant.application.dto;

import java.util.UUID;

public record PlanResponse(
    UUID id,
    String slug,
    String name,
    int priceMonthly,
    int priceYearly,
    int maxUsers,
    int maxStores,
    int maxProducts,
    int maxSalesPerMonth,
    int maxClients,
    int maxSuppliers,
    boolean featureExpenses,
    boolean featureReports,
    boolean featureAdvancedReports,
    boolean featureMultiPayment,
    boolean featureExportPdf,
    boolean featureExportExcel,
    boolean featureClientCredits,
    boolean featureSupplierTracking,
    boolean featureRoleManagement,
    boolean featureApi,
    boolean featureCustomBranding,
    boolean featurePrioritySupport,
    boolean featureAccountManager,
    boolean featureStockAlerts,
    boolean featureDeliveryCouriers,
    int dataRetentionMonths) {}
