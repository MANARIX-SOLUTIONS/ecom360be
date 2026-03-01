package com.ecom360.tenant.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "plan", uniqueConstraints = @UniqueConstraint(columnNames = "slug"))
public class Plan extends BaseEntity {

  @Column(nullable = false, unique = true)
  private String slug;

  @Column(nullable = false)
  private String name;

  @Column(name = "price_monthly", nullable = false)
  private Integer priceMonthly;

  @Column(name = "price_yearly", nullable = false)
  private Integer priceYearly;

  @Column(name = "max_users", nullable = false)
  private Integer maxUsers = 0;

  @Column(name = "max_stores", nullable = false)
  private Integer maxStores = 0;

  @Column(name = "max_products", nullable = false)
  private Integer maxProducts = 0;

  @Column(name = "max_sales_per_month", nullable = false)
  private Integer maxSalesPerMonth = 0;

  @Column(name = "max_clients", nullable = false)
  private Integer maxClients = 0;

  @Column(name = "max_suppliers", nullable = false)
  private Integer maxSuppliers = 0;

  @Column(name = "feature_expenses", nullable = false)
  private Boolean featureExpenses = false;

  @Column(name = "feature_reports", nullable = false)
  private Boolean featureReports = false;

  @Column(name = "feature_advanced_reports", nullable = false)
  private Boolean featureAdvancedReports = false;

  @Column(name = "feature_multi_payment", nullable = false)
  private Boolean featureMultiPayment = false;

  @Column(name = "feature_export_pdf", nullable = false)
  private Boolean featureExportPdf = false;

  @Column(name = "feature_export_excel", nullable = false)
  private Boolean featureExportExcel = false;

  @Column(name = "feature_client_credits", nullable = false)
  private Boolean featureClientCredits = false;

  @Column(name = "feature_supplier_tracking", nullable = false)
  private Boolean featureSupplierTracking = false;

  @Column(name = "feature_role_management", nullable = false)
  private Boolean featureRoleManagement = false;

  @Column(name = "feature_api", nullable = false)
  private Boolean featureApi = false;

  @Column(name = "feature_custom_branding", nullable = false)
  private Boolean featureCustomBranding = false;

  @Column(name = "feature_priority_support", nullable = false)
  private Boolean featurePrioritySupport = false;

  @Column(name = "feature_account_manager", nullable = false)
  private Boolean featureAccountManager = false;

  @Column(name = "feature_stock_alerts", nullable = false)
  private Boolean featureStockAlerts = false;

  @Column(name = "feature_delivery_couriers", nullable = false)
  private Boolean featureDeliveryCouriers = false;

  @Column(name = "data_retention_months", nullable = false)
  private Integer dataRetentionMonths = 0;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  public boolean isUnlimited(int limit) {
    return limit == 0;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getPriceMonthly() {
    return priceMonthly;
  }

  public void setPriceMonthly(Integer v) {
    this.priceMonthly = v;
  }

  public Integer getPriceYearly() {
    return priceYearly;
  }

  public void setPriceYearly(Integer v) {
    this.priceYearly = v;
  }

  public Integer getMaxUsers() {
    return maxUsers;
  }

  public void setMaxUsers(Integer v) {
    this.maxUsers = v;
  }

  public Integer getMaxStores() {
    return maxStores;
  }

  public void setMaxStores(Integer v) {
    this.maxStores = v;
  }

  public Integer getMaxProducts() {
    return maxProducts;
  }

  public void setMaxProducts(Integer v) {
    this.maxProducts = v;
  }

  public Integer getMaxSalesPerMonth() {
    return maxSalesPerMonth;
  }

  public void setMaxSalesPerMonth(Integer v) {
    this.maxSalesPerMonth = v;
  }

  public Integer getMaxClients() {
    return maxClients;
  }

  public void setMaxClients(Integer v) {
    this.maxClients = v;
  }

  public Integer getMaxSuppliers() {
    return maxSuppliers;
  }

  public void setMaxSuppliers(Integer v) {
    this.maxSuppliers = v;
  }

  public Boolean getFeatureExpenses() {
    return featureExpenses;
  }

  public void setFeatureExpenses(Boolean v) {
    this.featureExpenses = v;
  }

  public Boolean getFeatureReports() {
    return featureReports;
  }

  public void setFeatureReports(Boolean v) {
    this.featureReports = v;
  }

  public Boolean getFeatureAdvancedReports() {
    return featureAdvancedReports;
  }

  public void setFeatureAdvancedReports(Boolean v) {
    this.featureAdvancedReports = v;
  }

  public Boolean getFeatureMultiPayment() {
    return featureMultiPayment;
  }

  public void setFeatureMultiPayment(Boolean v) {
    this.featureMultiPayment = v;
  }

  public Boolean getFeatureExportPdf() {
    return featureExportPdf;
  }

  public void setFeatureExportPdf(Boolean v) {
    this.featureExportPdf = v;
  }

  public Boolean getFeatureExportExcel() {
    return featureExportExcel;
  }

  public void setFeatureExportExcel(Boolean v) {
    this.featureExportExcel = v;
  }

  public Boolean getFeatureClientCredits() {
    return featureClientCredits;
  }

  public void setFeatureClientCredits(Boolean v) {
    this.featureClientCredits = v;
  }

  public Boolean getFeatureSupplierTracking() {
    return featureSupplierTracking;
  }

  public void setFeatureSupplierTracking(Boolean v) {
    this.featureSupplierTracking = v;
  }

  public Boolean getFeatureRoleManagement() {
    return featureRoleManagement;
  }

  public void setFeatureRoleManagement(Boolean v) {
    this.featureRoleManagement = v;
  }

  public Boolean getFeatureApi() {
    return featureApi;
  }

  public void setFeatureApi(Boolean v) {
    this.featureApi = v;
  }

  public Boolean getFeatureCustomBranding() {
    return featureCustomBranding;
  }

  public void setFeatureCustomBranding(Boolean v) {
    this.featureCustomBranding = v;
  }

  public Boolean getFeaturePrioritySupport() {
    return featurePrioritySupport;
  }

  public void setFeaturePrioritySupport(Boolean v) {
    this.featurePrioritySupport = v;
  }

  public Boolean getFeatureAccountManager() {
    return featureAccountManager;
  }

  public void setFeatureAccountManager(Boolean v) {
    this.featureAccountManager = v;
  }

  public Boolean getFeatureStockAlerts() {
    return featureStockAlerts;
  }

  public void setFeatureStockAlerts(Boolean v) {
    this.featureStockAlerts = v;
  }

  public Boolean getFeatureDeliveryCouriers() {
    return featureDeliveryCouriers;
  }

  public void setFeatureDeliveryCouriers(Boolean v) {
    this.featureDeliveryCouriers = v;
  }

  public Integer getDataRetentionMonths() {
    return dataRetentionMonths;
  }

  public void setDataRetentionMonths(Integer v) {
    this.dataRetentionMonths = v;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean v) {
    this.isActive = v;
  }
}
