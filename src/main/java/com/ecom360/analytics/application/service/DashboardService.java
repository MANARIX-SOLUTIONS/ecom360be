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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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
    if (!p.hasBusinessAccess())
      throw new AccessDeniedException("Business context required");
    permissionService.requireAny(
        p, Permission.SALES_READ, Permission.PRODUCTS_READ, Permission.REPORTS_READ);
    UUID bId = p.businessId();

    Optional<Plan> planOpt = subscriptionService.getPlanForBusiness(bId);
    LocalDate today = LocalDate.now(ZoneId.systemDefault());
    EffectivePeriod ep = resolveEffectivePeriod(bId, periodStart, periodEnd, planOpt);
    boolean limitedAnalytics = planOpt.isPresent() && !Boolean.TRUE.equals(planOpt.get().getFeatureReports());
    boolean showLowStock = planOpt.isEmpty() || Boolean.TRUE.equals(planOpt.get().getFeatureStockAlerts());

    Instant todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

    long todaySalesCount = storeId != null
        ? saleRepo.countByBusinessIdAndStoreIdAndCreatedAtBetween(
            bId, storeId, todayStart, todayEnd)
        : saleRepo.countByBusinessIdAndCreatedAtBetween(bId, todayStart, todayEnd);

    PeriodSnapshot current = loadPeriodSnapshot(bId, storeId, ep.effStart(), ep.effEnd());
    long periodSalesCount = current.salesCount();
    long periodRevenue = current.revenue();
    long periodExpenses = current.expenses();
    long periodProfit = current.profit();

    long todayRevenue = sumCompletedRevenue(bId, storeId, todayStart, todayEnd);

    long todayExpenses = storeId != null
        ? expenseRepo.sumAmountByBusinessIdAndStoreIdAndDateBetween(bId, storeId, today, today)
        : expenseRepo.sumAmountByBusinessIdAndDateBetween(bId, today, today);

    long periodDays = ChronoUnit.DAYS.between(ep.effStart(), ep.effEnd()) + 1;
    LocalDate prevEnd = ep.effStart().minusDays(1);
    LocalDate prevStart = prevEnd.minusDays(periodDays - 1);
    PeriodSnapshot previous = loadPeriodSnapshot(bId, storeId, prevStart, prevEnd);

    long debtorClientsCount = clientRepo.countDebtorsWithPositiveBalance(bId);
    long totalReceivable = clientRepo.sumPositiveCreditBalance(bId);

    long totalProducts = productRepo.countByBusinessId(bId);
    long totalClients = clientRepo.countByBusinessIdAndIsActive(bId, true);
    long totalSuppliers = supplierRepo.countByBusinessIdAndIsActive(bId, true);
    List<Store> businessStores = storeRepo.findByBusinessId(bId);
    long totalStores = businessStores.size();

    List<DashboardResponse.LowStockItem> allLowStock = buildLowStockItems(storeId, showLowStock, businessStores);
    long lowStockItemsTotal = allLowStock.size();
    List<DashboardResponse.LowStockItem> lowStock = allLowStock.size() <= DASHBOARD_LIST_PREVIEW
        ? allLowStock
        : allLowStock.subList(0, DASHBOARD_LIST_PREVIEW);

    var recentPage = storeId != null
        ? saleRepo.findByBusinessIdAndStoreIdOrderByCreatedAtDesc(
            bId, storeId, PageRequest.of(0, 50))
        : saleRepo.findByBusinessIdOrderByCreatedAtDesc(bId, PageRequest.of(0, 50));
    List<DashboardResponse.RecentSale> recent = recentPage.stream()
        .filter(
            s -> !limitedAnalytics
                || (!s.getCreatedAt().isBefore(todayStart)
                    && s.getCreatedAt().isBefore(todayEnd)))
        .limit(10)
        .map(
            s -> new DashboardResponse.RecentSale(
                s.getId(),
                s.getReceiptNumber(),
                s.getTotal() != null ? s.getTotal() : 0,
                s.getPaymentMethod(),
                s.getStatus() != null ? s.getStatus() : "completed",
                s.getCreatedAt().toString()))
        .toList();

    List<Object[]> periodProductRows =
        saleLineRepo.aggregateProductSalesBetween(bId, storeId, ep.pStart(), ep.pEnd());
    List<DashboardResponse.TopProduct> allTopProducts =
        periodProductRows.stream().map(DashboardService::toTopProduct).toList();
    long topProductsTotal = allTopProducts.size();
    List<DashboardResponse.TopProduct> topProducts = allTopProducts.size() <= DASHBOARD_LIST_PREVIEW
        ? allTopProducts
        : allTopProducts.subList(0, DASHBOARD_LIST_PREVIEW);

    Long periodGrossMargin = null;
    List<DashboardResponse.TopMarginProduct> topMarginProducts = List.of();
    String businessCreatedAtIso = businessRepo.findById(bId).map(Business::getCreatedAt).map(Instant::toString)
        .orElse(null);

    if (planOpt.isPresent() && Boolean.TRUE.equals(planOpt.get().getFeatureAdvancedReports())) {
      Map<UUID, Integer> costByProduct = costByProduct(periodProductRows);
      long gm = 0;
      List<DashboardResponse.TopMarginProduct> margins = new ArrayList<>(periodProductRows.size());
      for (Object[] row : periodProductRows) {
        UUID productId = (UUID) row[0];
        String productName = (String) row[1];
        long quantity = asLong(row[2]);
        long lineRevenue = asLong(row[3]);
        long lineCost = (long) costByProduct.getOrDefault(productId, 0) * quantity;
        long margin = lineRevenue - lineCost;
        gm += margin;
        margins.add(new DashboardResponse.TopMarginProduct(productId, productName, margin));
      }
      periodGrossMargin = gm;
      topMarginProducts = margins.stream()
          .sorted(Comparator.comparingLong(DashboardResponse.TopMarginProduct::marginAmount).reversed())
          .limit(10)
          .toList();
    }

    String periodStartIso = ep.effStart().toString();
    String periodEndIso = ep.effEnd().toString();

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
        businessCreatedAtIso,
        periodStartIso,
        periodEndIso,
        previous.revenue(),
        previous.salesCount(),
        previous.expenses(),
        previous.profit(),
        debtorClientsCount,
        totalReceivable);
  }

  public DashboardSliceResponse<DashboardResponse.TopProduct> sliceTopProducts(
      UserPrincipal p,
      LocalDate periodStart,
      LocalDate periodEnd,
      UUID storeId,
      int page,
      int size) {
    if (!p.hasBusinessAccess())
      throw new AccessDeniedException("Business context required");
    permissionService.requireAny(
        p, Permission.SALES_READ, Permission.PRODUCTS_READ, Permission.REPORTS_READ);
    UUID bId = p.businessId();
    Optional<Plan> planOpt = subscriptionService.getPlanForBusiness(bId);
    EffectivePeriod ep = resolveEffectivePeriod(bId, periodStart, periodEnd, planOpt);
    List<DashboardResponse.TopProduct> all =
        saleLineRepo.aggregateProductSalesBetween(bId, storeId, ep.pStart(), ep.pEnd()).stream()
            .map(DashboardService::toTopProduct)
            .toList();
    return sliceList(all, page, size);
  }

  public DashboardSliceResponse<DashboardResponse.LowStockItem> sliceLowStockItems(
      UserPrincipal p, UUID storeId, int page, int size) {
    if (!p.hasBusinessAccess())
      throw new AccessDeniedException("Business context required");
    permissionService.requireAny(
        p, Permission.SALES_READ, Permission.PRODUCTS_READ, Permission.REPORTS_READ);
    UUID bId = p.businessId();
    Optional<Plan> planOpt = subscriptionService.getPlanForBusiness(bId);
    boolean showLowStock = planOpt.isEmpty() || Boolean.TRUE.equals(planOpt.get().getFeatureStockAlerts());
    int safeSize = Math.min(Math.max(size, 1), SLICE_MAX_SIZE);
    int safePage = Math.max(page, 0);
    if (!showLowStock) {
      return new DashboardSliceResponse<>(List.of(), 0, safePage, safeSize, false);
    }
    List<Store> businessStores = storeRepo.findByBusinessId(bId);
    List<DashboardResponse.LowStockItem> all = buildLowStockItems(storeId, true, businessStores);
    return sliceList(all, page, size);
  }

  public GlobalViewResponse getGlobalView(
      UserPrincipal p, LocalDate periodStart, LocalDate periodEnd) {
    if (!p.hasBusinessAccess())
      throw new AccessDeniedException("Business context required");
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
    boolean showLowStockGlobal = planG.isEmpty() || Boolean.TRUE.equals(planG.get().getFeatureStockAlerts());

    List<Store> stores = storeRepo.findByBusinessId(bId);
    Map<UUID, String> storeNames = new HashMap<>();
    for (Store s : stores)
      storeNames.put(s.getId(), s.getName());

    PeriodTotals periodTotals = loadPeriodTotals(bId, null, pStart, pEnd);
    long totalRevenue = periodTotals.revenue();
    long totalSalesCount = periodTotals.salesCount();
    double averageBasket = totalSalesCount > 0 ? (double) totalRevenue / totalSalesCount : 0;
    long totalExpenses = expenseRepo.sumAmountByBusinessIdAndDateBetween(bId, effStart, effEnd);
    long totalProfit = totalRevenue - totalExpenses;

    List<GlobalViewResponse.StoreStats> salesByStore = buildStoreStats(
        storeNames,
        saleRepo.sumRevenueAndCountByStoreIdBetween(bId, pStart, pEnd),
        expenseRepo.sumAmountGroupedByStoreIdBetween(bId, effStart, effEnd),
        totalRevenue,
        totalExpenses);

    List<DashboardResponse.LowStockItem> lowStock = buildLowStockItems(null, showLowStockGlobal, stores);

    List<DashboardResponse.TopProduct> topProducts =
        saleLineRepo.aggregateProductSalesBetween(bId, null, pStart, pEnd).stream()
            .limit(10)
            .map(DashboardService::toTopProduct)
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

  private List<GlobalViewResponse.StoreStats> buildStoreStats(
      Map<UUID, String> storeNames,
      List<Object[]> salesRows,
      List<Object[]> expenseRows,
      long totalRevenue,
      long totalExpenses) {
    Map<UUID, long[]> salesByStore = new HashMap<>();
    for (Object[] row : salesRows) {
      UUID storeId = (UUID) row[0];
      long revenue = row[1] instanceof Number n ? n.longValue() : 0L;
      long count = row[2] instanceof Number n ? n.longValue() : 0L;
      salesByStore.put(storeId, new long[] { revenue, count });
    }

    Map<UUID, Long> expensesByStore = new HashMap<>();
    long unassignedExpenses = 0L;
    for (Object[] row : expenseRows) {
      UUID storeId = (UUID) row[0];
      long amount = row[1] instanceof Number n ? n.longValue() : 0L;
      if (storeId == null) {
        unassignedExpenses = amount;
      } else {
        expensesByStore.put(storeId, amount);
      }
    }

    Set<UUID> activeStoreIds = new HashSet<>();
    activeStoreIds.addAll(salesByStore.keySet());
    activeStoreIds.addAll(expensesByStore.keySet());

    List<GlobalViewResponse.StoreStats> stats = new ArrayList<>();
    for (UUID storeId : activeStoreIds) {
      long[] sales = salesByStore.getOrDefault(storeId, new long[] { 0, 0 });
      long revenue = sales[0];
      long salesCount = sales[1];
      long expenses = expensesByStore.getOrDefault(storeId, 0L);
      stats.add(
          new GlobalViewResponse.StoreStats(
              storeId,
              storeNames.getOrDefault(storeId, "Boutique"),
              revenue,
              salesCount,
              roundSharePercent(totalRevenue, revenue),
              expenses,
              revenue - expenses,
              roundSharePercent(totalExpenses, expenses)));
    }

    if (unassignedExpenses > 0) {
      stats.add(
          new GlobalViewResponse.StoreStats(
              null,
              "Communes",
              0,
              0,
              0,
              unassignedExpenses,
              -unassignedExpenses,
              roundSharePercent(totalExpenses, unassignedExpenses)));
    }

    stats.sort(
        Comparator.comparingLong(GlobalViewResponse.StoreStats::revenue)
            .reversed()
            .thenComparingLong(GlobalViewResponse.StoreStats::expenses)
            .reversed());
    return stats;
  }

  private static double roundSharePercent(long total, long part) {
    return total > 0 ? Math.round(1000.0 * part / total) / 10.0 : 0;
  }

  private record PeriodSnapshot(long revenue, long salesCount, long expenses, long profit) {
  }

  private record PeriodTotals(long revenue, long salesCount) {
  }

  private record LowStockEntry(UUID productId, String storeName, int quantity, int minStock) {
  }

  /**
   * Agrège ventes complétées et dépenses sur {@code effStart}–{@code effEnd}
   * inclus (dates locales) via des requêtes SQL, sans matérialiser les ventes.
   */
  private PeriodSnapshot loadPeriodSnapshot(
      UUID bId, UUID storeId, LocalDate effStart, LocalDate effEnd) {
    Instant pStart = effStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant pEnd = effEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    PeriodTotals totals = loadPeriodTotals(bId, storeId, pStart, pEnd);
    long expenses = storeId != null
        ? expenseRepo.sumAmountByBusinessIdAndStoreIdAndDateBetween(
            bId, storeId, effStart, effEnd)
        : expenseRepo.sumAmountByBusinessIdAndDateBetween(bId, effStart, effEnd);
    return new PeriodSnapshot(
        totals.revenue(), totals.salesCount(), expenses, totals.revenue() - expenses);
  }

  private PeriodTotals loadPeriodTotals(UUID bId, UUID storeId, Instant start, Instant end) {
    Object[] row = saleRepo.sumRevenueAndCountBetween(bId, storeId, start, end);
    return new PeriodTotals(revenueOf(row), countOf(row));
  }

  private long sumCompletedRevenue(UUID bId, UUID storeId, Instant start, Instant end) {
    return revenueOf(saleRepo.sumRevenueAndCountBetween(bId, storeId, start, end));
  }

  private Map<UUID, Integer> costByProduct(List<Object[]> productRows) {
    Set<UUID> productIds = new HashSet<>();
    for (Object[] row : productRows) {
      productIds.add((UUID) row[0]);
    }
    if (productIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, Integer> costByProduct = new HashMap<>();
    for (Product product : productRepo.findAllById(productIds)) {
      costByProduct.put(product.getId(), product.getCostPrice());
    }
    return costByProduct;
  }

  private static DashboardResponse.TopProduct toTopProduct(Object[] row) {
    return new DashboardResponse.TopProduct(
        (UUID) row[0], (String) row[1], asLong(row[2]), asLong(row[3]));
  }

  private static long revenueOf(Object[] row) {
    return row != null && row.length > 0 ? asLong(row[0]) : 0L;
  }

  private static long countOf(Object[] row) {
    return row != null && row.length > 1 ? asLong(row[1]) : 0L;
  }

  private static long asLong(Object value) {
    return value instanceof Number n ? n.longValue() : 0L;
  }

  private record EffectivePeriod(
      LocalDate effStart, LocalDate effEnd, Instant pStart, Instant pEnd) {
  }

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

  private List<DashboardResponse.LowStockItem> buildLowStockItems(
      UUID storeId, boolean showLowStock, List<Store> businessStores) {
    List<DashboardResponse.LowStockItem> lowStock = new ArrayList<>();
    if (!showLowStock) {
      return lowStock;
    }
    List<Store> storesForStock = storeId != null
        ? businessStores.stream().filter(s -> s.getId().equals(storeId)).toList()
        : businessStores;

    List<LowStockEntry> entries = new ArrayList<>();
    Set<UUID> productIds = new HashSet<>();
    for (Store store : storesForStock) {
      for (ProductStoreStock s : stockRepo.findByStoreId(store.getId())) {
        if (s.isLowStock()) {
          entries.add(
              new LowStockEntry(
                  s.getProductId(), store.getName(), s.getQuantity(), s.getMinStock()));
          productIds.add(s.getProductId());
        }
      }
    }

    Map<UUID, String> productNames = new HashMap<>();
    if (!productIds.isEmpty()) {
      for (Product pr : productRepo.findAllById(productIds)) {
        productNames.put(pr.getId(), pr.getName());
      }
    }

    for (LowStockEntry e : entries) {
      lowStock.add(
          new DashboardResponse.LowStockItem(
              e.productId(),
              productNames.getOrDefault(e.productId(), "Unknown"),
              e.storeName(),
              e.quantity(),
              e.minStock()));
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
