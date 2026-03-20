package com.ecom360.analytics.application.service;

import com.ecom360.analytics.application.dto.DashboardResponse;
import com.ecom360.analytics.application.dto.DashboardSliceResponse;
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
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
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

  private static final int DASHBOARD_LIST_PREVIEW = 10;
  private static final int SLICE_MAX_SIZE = 50;

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
  private final BusinessRepository businessRepo;

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
      SubscriptionService subscriptionService,
      BusinessRepository businessRepo) {
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
    this.businessRepo = businessRepo;
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
    EffectivePeriod ep = resolveEffectivePeriod(bId, periodStart, periodEnd, planOpt);
    LocalDate effStart = ep.effStart();
    LocalDate effEnd = ep.effEnd();
    boolean limitedAnalytics =
        planOpt.isPresent() && !Boolean.TRUE.equals(planOpt.get().getFeatureReports());
    boolean showLowStock =
        planOpt.isEmpty() || Boolean.TRUE.equals(planOpt.get().getFeatureStockAlerts());

    Instant todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant pStart = ep.pStart();
    Instant pEnd = ep.pEnd();

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

    long todayExpenses =
        storeId != null
            ? expenseRepo.sumAmountByBusinessIdAndStoreIdAndDateBetween(bId, storeId, today, today)
            : expenseRepo.sumAmountByBusinessIdAndDateBetween(bId, today, today);

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

    List<DashboardResponse.LowStockItem> allLowStock =
        buildLowStockItems(storeId, showLowStock, businessStores);
    long lowStockItemsTotal = allLowStock.size();
    List<DashboardResponse.LowStockItem> lowStock =
        allLowStock.size() <= DASHBOARD_LIST_PREVIEW
            ? allLowStock
            : allLowStock.subList(0, DASHBOARD_LIST_PREVIEW);

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

    List<DashboardResponse.TopProduct> allTopProducts = topProductsFromPeriodSales(periodSales);
    long topProductsTotal = allTopProducts.size();
    List<DashboardResponse.TopProduct> topProducts =
        allTopProducts.size() <= DASHBOARD_LIST_PREVIEW
            ? allTopProducts
            : allTopProducts.subList(0, DASHBOARD_LIST_PREVIEW);

    Long periodGrossMargin = null;
    List<DashboardResponse.TopMarginProduct> topMarginProducts = List.of();
    String businessCreatedAtIso =
        businessRepo
            .findById(bId)
            .map(Business::getCreatedAt)
            .map(Instant::toString)
            .orElse(null);

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
        todayExpenses,
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
        topMarginProducts,
        topProductsTotal,
        lowStockItemsTotal,
        businessCreatedAtIso);
  }

  public DashboardSliceResponse<DashboardResponse.TopProduct> sliceTopProducts(
      UserPrincipal p,
      LocalDate periodStart,
      LocalDate periodEnd,
      UUID storeId,
      int page,
      int size) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
    UUID bId = p.businessId();
    Optional<Plan> planOpt = subscriptionService.getPlanForBusiness(bId);
    EffectivePeriod ep = resolveEffectivePeriod(bId, periodStart, periodEnd, planOpt);
    List<DashboardResponse.TopProduct> all =
        buildTopProductsForPeriod(bId, storeId, ep.pStart(), ep.pEnd());
    return sliceList(all, page, size);
  }

  public DashboardSliceResponse<DashboardResponse.LowStockItem> sliceLowStockItems(
      UserPrincipal p, UUID storeId, int page, int size) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
    UUID bId = p.businessId();
    Optional<Plan> planOpt = subscriptionService.getPlanForBusiness(bId);
    boolean showLowStock =
        planOpt.isEmpty() || Boolean.TRUE.equals(planOpt.get().getFeatureStockAlerts());
    int safeSize = Math.min(Math.max(size, 1), SLICE_MAX_SIZE);
    int safePage = Math.max(page, 0);
    if (!showLowStock) {
      return new DashboardSliceResponse<>(List.of(), 0, safePage, safeSize, false);
    }
    List<Store> businessStores = storeRepo.findByBusinessId(bId);
    List<DashboardResponse.LowStockItem> all =
        buildLowStockItems(storeId, true, businessStores);
    return sliceList(all, page, size);
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

  private record EffectivePeriod(
      LocalDate effStart, LocalDate effEnd, Instant pStart, Instant pEnd) {}

  private EffectivePeriod resolveEffectivePeriod(
      UUID businessId, LocalDate periodStart, LocalDate periodEnd, Optional<Plan> planOpt) {
    LocalDate today = LocalDate.now(ZoneId.systemDefault());
    LocalDate effStart = periodStart;
    LocalDate effEnd = periodEnd;
    if (planOpt.isPresent() && !Boolean.TRUE.equals(planOpt.get().getFeatureReports())) {
      effStart = today;
      effEnd = today;
    }
    effStart = subscriptionService.clampPeriodStartToRetention(businessId, effStart);
    if (effEnd.isBefore(effStart)) {
      effEnd = effStart;
    }
    Instant pStart = effStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant pEnd = effEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    return new EffectivePeriod(effStart, effEnd, pStart, pEnd);
  }

  private List<DashboardResponse.TopProduct> buildTopProductsForPeriod(
      UUID bId, UUID storeId, Instant pStart, Instant pEnd) {
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
    return topProductsFromPeriodSales(periodSales);
  }

  private List<DashboardResponse.TopProduct> topProductsFromPeriodSales(List<Sale> periodSales) {
    Map<UUID, long[]> productStats = new HashMap<>();
    for (Sale sale : periodSales) {
      for (SaleLine line : saleLineRepo.findBySaleId(sale.getId())) {
        productStats.computeIfAbsent(line.getProductId(), k -> new long[] {0, 0});
        productStats.get(line.getProductId())[0] += line.getQuantity();
        productStats.get(line.getProductId())[1] += line.getLineTotal();
      }
    }
    return productStats.entrySet().stream()
        .sorted(
            (a, b) -> {
              int cmp = Long.compare(b.getValue()[1], a.getValue()[1]);
              if (cmp != 0) return cmp;
              return a.getKey().toString().compareTo(b.getKey().toString());
            })
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
  }

  private List<DashboardResponse.LowStockItem> buildLowStockItems(
      UUID storeId, boolean showLowStock, List<Store> businessStores) {
    List<DashboardResponse.LowStockItem> lowStock = new ArrayList<>();
    if (!showLowStock) {
      return lowStock;
    }
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
    lowStock.sort(
        Comparator.comparing(DashboardResponse.LowStockItem::storeName)
            .thenComparing(DashboardResponse.LowStockItem::productName));
    return lowStock;
  }

  private static <T> DashboardSliceResponse<T> sliceList(List<T> all, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), SLICE_MAX_SIZE);
    int safePage = Math.max(page, 0);
    long total = all.size();
    int from = safePage * safeSize;
    if (from >= total) {
      return new DashboardSliceResponse<>(List.of(), total, safePage, safeSize, false);
    }
    int to = Math.min(from + safeSize, (int) total);
    boolean hasNext = to < total;
    return new DashboardSliceResponse<>(all.subList(from, to), total, safePage, safeSize, hasNext);
  }
}
