package com.ecom360.analytics.application.service;

import com.ecom360.analytics.application.dto.DashboardResponse;
import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.client.domain.repository.ClientRepository;
import com.ecom360.expense.domain.repository.ExpenseRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

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

    public DashboardService(SaleRepository saleRepo, SaleLineRepository saleLineRepo, ProductRepository productRepo, StoreRepository storeRepo, ClientRepository clientRepo, SupplierRepository supplierRepo, ExpenseRepository expenseRepo, ProductStoreStockRepository stockRepo) {
        this.saleRepo = saleRepo; this.saleLineRepo = saleLineRepo; this.productRepo = productRepo; this.storeRepo = storeRepo; this.clientRepo = clientRepo; this.supplierRepo = supplierRepo; this.expenseRepo = expenseRepo; this.stockRepo = stockRepo;
    }

    public DashboardResponse getDashboard(UserPrincipal p, LocalDate periodStart, LocalDate periodEnd) {
        if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
        UUID bId = p.businessId();

        Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant todayEnd = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant pStart = periodStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant pEnd = periodEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        long todaySalesCount = saleRepo.countByBusinessIdAndCreatedAtBetween(bId, todayStart, todayEnd);
        long periodSalesCount = saleRepo.countByBusinessIdAndCreatedAtBetween(bId, pStart, pEnd);

        // Revenue from completed sales
        Pageable all = Pageable.unpaged();
        List<Sale> periodSales = saleRepo.findByBusinessIdOrderByCreatedAtDesc(bId, all).stream()
                .filter(s -> s.isCompleted() && !s.getCreatedAt().isBefore(pStart) && s.getCreatedAt().isBefore(pEnd)).toList();
        long periodRevenue = periodSales.stream().mapToLong(s -> s.getTotal() != null ? s.getTotal() : 0).sum();
        long todayRevenue = periodSales.stream().filter(s -> !s.getCreatedAt().isBefore(todayStart) && s.getCreatedAt().isBefore(todayEnd)).mapToLong(s -> s.getTotal() != null ? s.getTotal() : 0).sum();

        long periodExpenses = expenseRepo.sumAmountByBusinessIdAndDateBetween(bId, periodStart, periodEnd);
        long periodProfit = periodRevenue - periodExpenses;

        long totalProducts = productRepo.findByBusinessId(bId, Pageable.unpaged()).getTotalElements();
        long totalClients = clientRepo.findByBusinessIdAndIsActive(bId, true, Pageable.unpaged()).getTotalElements();
        long totalSuppliers = supplierRepo.findByBusinessIdAndIsActive(bId, true, Pageable.unpaged()).getTotalElements();
        long totalStores = storeRepo.findByBusinessId(bId).size();

        // Low stock
        List<DashboardResponse.LowStockItem> lowStock = new ArrayList<>();
        for (Store store : storeRepo.findByBusinessId(bId)) {
            for (ProductStoreStock s : stockRepo.findByStoreId(store.getId())) {
                if (s.isLowStock()) {
                    Product pr = productRepo.findById(s.getProductId()).orElse(null);
                    lowStock.add(new DashboardResponse.LowStockItem(s.getProductId(), pr != null ? pr.getName() : "Unknown", store.getName(), s.getQuantity(), s.getMinStock()));
                }
            }
        }

        // Recent sales
        List<DashboardResponse.RecentSale> recent = saleRepo.findByBusinessIdOrderByCreatedAtDesc(bId, PageRequest.of(0, 10)).stream()
                .map(s -> new DashboardResponse.RecentSale(s.getId(), s.getReceiptNumber(), s.getTotal() != null ? s.getTotal() : 0, s.getPaymentMethod(), s.getCreatedAt().toString())).toList();

        // Top products
        Map<UUID, long[]> productStats = new HashMap<>();
        for (Sale sale : periodSales) {
            for (SaleLine line : saleLineRepo.findBySaleId(sale.getId())) {
                productStats.computeIfAbsent(line.getProductId(), k -> new long[]{0, 0});
                productStats.get(line.getProductId())[0] += line.getQuantity();
                productStats.get(line.getProductId())[1] += line.getLineTotal();
            }
        }
        List<DashboardResponse.TopProduct> topProducts = productStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .limit(10)
                .map(e -> { Product pr = productRepo.findById(e.getKey()).orElse(null); return new DashboardResponse.TopProduct(e.getKey(), pr != null ? pr.getName() : "Unknown", e.getValue()[0], e.getValue()[1]); })
                .toList();

        return new DashboardResponse(todaySalesCount, todayRevenue, periodSalesCount, periodRevenue, periodExpenses, periodProfit, totalProducts, totalClients, totalSuppliers, totalStores, lowStock, recent, topProducts);
    }
}
