package com.ecom360.analytics.application.service;

import com.ecom360.analytics.application.dto.DashboardResponse;
import com.ecom360.analytics.application.dto.GlobalViewResponse;
import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.client.domain.repository.ClientRepository;
import com.ecom360.expense.domain.repository.ExpenseRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.inventory.domain.model.ProductStoreStock;
import com.ecom360.inventory.domain.repository.ProductStoreStockRepository;
import com.ecom360.sales.domain.model.Sale;
import com.ecom360.sales.domain.model.SaleLine;
import com.ecom360.sales.domain.repository.SaleLineRepository;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.store.domain.model.Store;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.supplier.domain.repository.SupplierRepository;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.domain.model.Plan;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  private final SaleRepository saleRepo;
  private final SaleLineRepository saleLineRepo;
  private final ProductRepository productRepo;
  private final StoreRepository storeRepo;
  private final ClientRepository clientRepo;
  private final SupplierRepository supplierRepo;
  private final ExpenseRepository expenseRepo;
  private final ProductStoreStockRepository stockRepo;
  private final RolePermissionService permissionService;
  private final SubscriptionService subscriptionService;

  public DashboardService(
      SaleRepository saleRepo,
      SaleLineRepository saleLineRepo,
      ProductRepository productRepo,
      StoreRepository storeRepo,
      ClientRepository clientRepo,
      SupplierRepository supplierRepo,
      ExpenseRepository expenseRepo,
      ProductStoreStockRepository stockRepo,
      RolePermissionService permissionService,
      SubscriptionService subscriptionService) {
    this.saleRepo = saleRepo;
    this.saleLineRepo = saleLineRepo;
    this.productRepo = productRepo;
    this.storeRepo = storeRepo;
    this.clientRepo = clientRepo;
    this.supplierRepo = supplierRepo;
    this.expenseRepo = expenseRepo;
    this.stockRepo = stockRepo;
    this.permissionService = permissionService;
    this.subscriptionService = subscriptionService;
  }

  public DashboardResponse getDashboard(
      UserPrincipal p, LocalDate periodStart, LocalDate periodEnd) {
    return getDashboard(p, periodStart, periodEnd, null);
  }

  public DashboardResponse getDashboard(
      UserPrincipal p, LocalDate periodStart, LocalDate periodEnd, UUID storeId) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
    UUID bId = p.businessId();

    Optional<Plan> planOpt = subscriptionService.getPlanForBusiness(bId);
    LocalDate today = LocalDate.now(ZoneId.systemDefault());
    LocalDate effStart = periodStart;
    LocalDate effEnd = periodEnd;
    if (planOpt.isPresent() && !Boolean.TRUE.equals(planOpt.get().getFeatureReports())) {
      effStart = today;
      effEnd = today;
    }
    effStart = subscriptionService.clampPeriodStartToRetention(bId, effStart);
    if (effEnd.isBefore(effStart)) {
      effEnd = effStart;
    }
    boolean limitedAnalytics =
        planOpt.isPresent() && !Boolean.TRUE.equals(planOpt.get().getFeatureReports());
    boolean showLowStock =
        planOpt.isEmpty() || Boolean.TRUE.equals(planOpt.get().getFeatureStockAlerts());

    Instant todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant pStart = effStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant pEnd = effEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

    long todaySalesCount =
        storeId != null
            ? saleRepo.countByBusinessIdAndStoreIdAndCreatedAtBetween(bId, storeId, todayStart, todayEnd)
            : saleRepo.countByBusinessIdAndCreatedAtBetween(bId, todayStart, todayEnd);
    long periodSalesCount =
        storeId != null
            ? saleRepo.countByBusinessIdAndStoreIdAndCreatedAtBetween(bId, storeId, pStart, pEnd)
            : saleRepo.countByBusinessIdAndCreatedAtBetween(bId, pStart, pEnd);

    // Revenue from completed sales
    Pageable all = Pageable.unpaged();
    List<Sale> periodSales =
        (storeId != null
                ? saleRepo.findByBusinessIdAndStoreIdOrderByCreatedAtDesc(bId, storeId, all)
                : saleRepo.findByBusinessIdOrderByCreatedAtDesc(bId, all))
            .stream()
            .filter(
                s ->
                    s.isCompleted()
                        && !s.getCreatedAt().isBefore(pStart)
                        && s.getCreatedAt().isBefore(pEnd))
            .toList();
    long periodRevenue =
        periodSales.stream().mapToLong(s -> s.getTotal() != null ? s.getTotal() : 0).sum();
    long todayRevenue =
        periodSales.stream()
            .filter(
                s -> !s.getCreatedAt().isBefore(todayStart) && s.getCreatedAt().isBefore(todayEnd))
            .mapToLong(s -> s.getTotal() != null ? s.getTotal() : 0)
            .sum();

    long periodExpenses =
        storeId != null
            ? expenseRepo.sumAmountByBusinessIdAndStoreIdAndDateBetween(
                bId, storeId, effStart, effEnd)
            : expenseRepo.sumAmountByBusinessIdAndDateBetween(bId, effStart, effEnd);
    long periodProfit = periodRevenue - periodExpenses;

    long totalProducts = productRepo.findByBusinessId(bId, Pageable.unpaged()).getTotalElements();
    long totalClients =
        clientRepo.findByBusinessIdAndIsActive(bId, true, Pageable.unpaged()).getTotalElements();
    long totalSuppliers =
        supplierRepo.findByBusinessIdAndIsActive(bId, true, Pageable.unpaged()).getTotalElements();
    List<Store> businessStores = storeRepo.findByBusinessId(bId);
    long totalStores = businessStores.size();

    List<DashboardResponse.LowStockItem> lowStock = new ArrayList<>();
    if (showLowStock) {
      List<Store> storesForStock =
          storeId != null
              ? businessStores.stream().filter(s -> s.getId().equals(storeId)).toList()
              : businessStores;
      for (Store store : storesForStock) {
        for (ProductStoreStock s : stockRepo.findByStoreId(store.getId())) {
          if (s.isLowStock()) {
            Product pr = productRepo.findById(s.getProductId()).orElse(null);
            lowStock.add(
                new DashboardResponse.LowStockItem(
                    s.getProductId(),
                    pr != null ? pr.getName() : "Unknown",
                    store.getName(),
                    s.getQuantity(),
                    s.getMinStock()));
          }
        }
      }
    }

    var recentPage =
        storeId != null
            ? saleRepo.findByBusinessIdAndStoreIdOrderByCreatedAtDesc(bId, storeId, PageRequest.of(0, 50))
            : saleRepo.findByBusinessIdOrderByCreatedAtDesc(bId, PageRequest.of(0, 50));
    List<DashboardResponse.RecentSale> recent =
        recentPage.stream()
            .filter(
                s ->
                    !limitedAnalytics
                        || (!s.getCreatedAt().isBefore(todayStart)
                            && s.getCreatedAt().isBefore(todayEnd)))
            .limit(10)
            .map(
                s ->
                    new DashboardResponse.RecentSale(
                        s.getId(),
                        s.getReceiptNumber(),
                        s.getTotal() != null ? s.getTotal() : 0,
                        s.getPaymentMethod(),
                        s.getStatus() != null ? s.getStatus() : "completed",
                        s.getCreatedAt().toString()))
            .toList();

    // Top products
    Map<UUID, long[]> productStats = new HashMap<>();
    for (Sale sale : periodSales) {
      for (SaleLine line : saleLineRepo.findBySaleId(sale.getId())) {
        productStats.computeIfAbsent(line.getProductId(), k -> new long[] {0, 0});
        productStats.get(line.getProductId())[0] += line.getQuantity();
        productStats.get(line.getProductId())[1] += line.getLineTotal();
      }
    }
    List<DashboardResponse.TopProduct> topProducts =
        productStats.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
            .limit(10)
            .map(
                e -> {
                  Product pr = productRepo.findById(e.getKey()).orElse(null);
                  return new DashboardResponse.TopProduct(
                      e.getKey(),
                      pr != null ? pr.getName() : "Unknown",
                      e.getValue()[0],
                      e.getValue()[1]);
                })
            .toList();

    Long periodGrossMargin = null;
    List<DashboardResponse.TopMarginProduct> topMarginProducts = List.of();
    if (planOpt.isPresent()
        && Boolean.TRUE.equals(planOpt.get().getFeatureAdvancedReports())) {
      long gm = 0;
      Map<UUID, Long> marginByProduct = new HashMap<>();
      for (Sale sale : periodSales) {
        if (!sale.isCompleted()) continue;
        for (SaleLine line : saleLineRepo.findBySaleId(sale.getId())) {
          Product pr = productRepo.findById(line.getProductId()).orElse(null);
          int cost = pr != null ? pr.getCostPrice() : 0;
          int lineRev = line.getLineTotal();
          long lineCost = (long) cost * line.getQuantity();
          long m = lineRev - lineCost;
          gm += m;
          marginByProduct.merge(line.getProductId(), m, Long::sum);
        }
      }
      periodGrossMargin = gm;
      topMarginProducts =
          marginByProduct.entrySet().stream()
              .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
              .limit(10)
              .map(
                  e -> {
                    Product pr = productRepo.findById(e.getKey()).orElse(null);
                    return new DashboardResponse.TopMarginProduct(
                        e.getKey(), pr != null ? pr.getName() : "?", e.getValue());
                  })
              .toList();
    }

    return new DashboardResponse(
        todaySalesCount,
        todayRevenue,
        periodSalesCount,
        periodRevenue,
        periodExpenses,
        periodProfit,
        totalProducts,
        totalClients,
        totalSuppliers,
        totalStores,
        lowStock,
        recent,
        topProducts,
        limitedAnalytics,
        periodGrossMargin,
        topMarginProducts);
  }

  public GlobalViewResponse getGlobalView(
      UserPrincipal p, LocalDate periodStart, LocalDate periodEnd) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
    permissionService.require(p, Permission.GLOBAL_VIEW_READ);
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureGlobalView())) {
                throw new AccessDeniedException(
                    "Vue globale réservée aux plans Pro et Business. Passez à un plan supérieur.");
              }
            });
    subscriptionService.requireFeatureReports(p.businessId());
    UUID bId = p.businessId();
    LocalDate effStart = subscriptionService.clampPeriodStartToRetention(bId, periodStart);
    LocalDate effEnd = periodEnd.isBefore(effStart) ? effStart : periodEnd;
    Instant pStart = effStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant pEnd = effEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

    Optional<Plan> planG = subscriptionService.getPlanForBusiness(bId);
    boolean showLowStockGlobal =
        planG.isEmpty() || Boolean.TRUE.equals(planG.get().getFeatureStockAlerts());

    List<Store> stores = storeRepo.findByBusinessId(bId);
    Map<UUID, String> storeNames = new HashMap<>();
    for (Store s : stores) storeNames.put(s.getId(), s.getName());

    List<Sale> periodSales =
        saleRepo.findByBusinessIdOrderByCreatedAtDesc(bId, Pageable.unpaged()).stream()
            .filter(
                s ->
                    s.isCompleted()
                        && !s.getCreatedAt().isBefore(pStart)
                        && s.getCreatedAt().isBefore(pEnd))
            .toList();

    long totalRevenue =
        periodSales.stream().mapToLong(s -> s.getTotal() != null ? s.getTotal() : 0).sum();
    long totalSalesCount = periodSales.size();
    double averageBasket = totalSalesCount > 0 ? (double) totalRevenue / totalSalesCount : 0;
    long totalExpenses =
        expenseRepo.sumAmountByBusinessIdAndDateBetween(bId, effStart, effEnd);
    long totalProfit = totalRevenue - totalExpenses;

    List<Object[]> storeRows =
        saleRepo.sumRevenueAndCountByStoreIdBetween(bId, pStart, pEnd);
    List<GlobalViewResponse.StoreStats> salesByStore = new ArrayList<>();
    for (Object[] row : storeRows) {
      UUID storeId = (UUID) row[0];
      long revenue = row[1] instanceof Number n ? n.longValue() : 0L;
      long count = row[2] instanceof Number n ? n.longValue() : 0L;
      double sharePercent = totalRevenue > 0 ? (100.0 * revenue / totalRevenue) : 0;
      salesByStore.add(
          new GlobalViewResponse.StoreStats(
              storeId,
              storeNames.getOrDefault(storeId, "Boutique"),
              revenue,
              count,
              Math.round(sharePercent * 10) / 10.0));
    }
    salesByStore.sort((a, b) -> Long.compare(b.revenue(), a.revenue()));

    List<DashboardResponse.LowStockItem> lowStock = new ArrayList<>();
    if (showLowStockGlobal) {
      for (Store store : stores) {
        for (ProductStoreStock s : stockRepo.findByStoreId(store.getId())) {
          if (s.isLowStock()) {
            Product pr = productRepo.findById(s.getProductId()).orElse(null);
            lowStock.add(
                new DashboardResponse.LowStockItem(
                    s.getProductId(),
                    pr != null ? pr.getName() : "Unknown",
                    store.getName(),
                    s.getQuantity(),
                    s.getMinStock()));
          }
        }
      }
    }

    Map<UUID, long[]> productStats = new HashMap<>();
    for (Sale sale : periodSales) {
      for (SaleLine line : saleLineRepo.findBySaleId(sale.getId())) {
        productStats.computeIfAbsent(line.getProductId(), k -> new long[] {0, 0});
        productStats.get(line.getProductId())[0] += line.getQuantity();
        productStats.get(line.getProductId())[1] += line.getLineTotal();
      }
    }
    List<DashboardResponse.TopProduct> topProducts =
        productStats.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
            .limit(10)
            .map(
                e -> {
                  Product pr = productRepo.findById(e.getKey()).orElse(null);
                  return new DashboardResponse.TopProduct(
                      e.getKey(),
                      pr != null ? pr.getName() : "Unknown",
                      e.getValue()[0],
                      e.getValue()[1]);
                })
            .toList();

    return new GlobalViewResponse(
        effStart,
        effEnd,
        totalRevenue,
        totalSalesCount,
        averageBasket,
        totalExpenses,
        totalProfit,
        stores.size(),
        salesByStore,
        lowStock,
        topProducts);
  }
}
