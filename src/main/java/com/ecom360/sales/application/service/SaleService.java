package com.ecom360.sales.application.service;

import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.client.domain.model.Client;
import com.ecom360.client.domain.repository.ClientRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.inventory.application.service.StockService;
import com.ecom360.sales.application.dto.*;
import com.ecom360.sales.domain.model.*;
import com.ecom360.sales.domain.repository.*;
import com.ecom360.shared.domain.exception.*;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {
  private final SaleRepository saleRepo;
  private final SaleLineRepository lineRepo;
  private final ProductRepository productRepo;
  private final StoreRepository storeRepo;
  private final ClientRepository clientRepo;
  private final StockService stockService;
  private final SubscriptionService subscriptionService;
  private final RolePermissionService permissionService;

  public SaleService(
      SaleRepository saleRepo,
      SaleLineRepository lineRepo,
      ProductRepository productRepo,
      StoreRepository storeRepo,
      ClientRepository clientRepo,
      StockService stockService,
      SubscriptionService subscriptionService,
      RolePermissionService permissionService) {
    this.saleRepo = saleRepo;
    this.lineRepo = lineRepo;
    this.productRepo = productRepo;
    this.storeRepo = storeRepo;
    this.clientRepo = clientRepo;
    this.stockService = stockService;
    this.subscriptionService = subscriptionService;
    this.permissionService = permissionService;
  }

  @Transactional
  public SaleResponse createSale(SaleRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_CREATE);
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!plan.isUnlimited(plan.getMaxSalesPerMonth())) {
                ZoneId zone = ZoneId.systemDefault();
                LocalDate now = LocalDate.now(zone);
                Instant start = now.withDayOfMonth(1).atStartOfDay(zone).toInstant();
                Instant end = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant();
                long count =
                    saleRepo.countByBusinessIdAndCreatedAtBetween(p.businessId(), start, end);
                if (count >= plan.getMaxSalesPerMonth()) {
                  throw new BusinessRuleException(
                      "Limite du plan atteinte : maximum "
                          + plan.getMaxSalesPerMonth()
                          + " ventes/mois. Passez à un plan supérieur.");
                }
              }
              if ("credit".equals(req.paymentMethod())
                  && !Boolean.TRUE.equals(plan.getFeatureClientCredits())) {
                throw new BusinessRuleException(
                    "Ventes à crédit non incluses dans votre plan. Passez à un plan supérieur.");
              }
              if (("wave".equals(req.paymentMethod()) || "orange_money".equals(req.paymentMethod()))
                  && !Boolean.TRUE.equals(plan.getFeatureMultiPayment())) {
                throw new BusinessRuleException(
                    "Paiement mobile (Wave, Orange Money) non inclus dans votre plan. Passez à un plan supérieur.");
              }
            });
    if ("credit".equals(req.paymentMethod()) && req.clientId() == null) {
      throw new BusinessRuleException("Client is required for credit sales");
    }
    storeRepo
        .findById(req.storeId())
        .filter(s -> s.belongsTo(p.businessId()))
        .orElseThrow(() -> new ResourceNotFoundException("Store", req.storeId()));
    if (req.clientId() != null)
      clientRepo
          .findByBusinessIdAndId(p.businessId(), req.clientId())
          .orElseThrow(() -> new ResourceNotFoundException("Client", req.clientId()));

    Sale sale = new Sale();
    sale.setBusinessId(p.businessId());
    sale.setStoreId(req.storeId());
    sale.setUserId(p.userId());
    sale.setClientId(req.clientId());
    sale.setPaymentMethod(req.paymentMethod());
    sale.setDiscountAmount(req.discountAmount());
    sale.setNote(req.note());
    sale.setReceiptNumber(generateReceiptNumber());
    sale.setStatus("completed");
    sale.setSubtotal(0);
    sale.setTotal(0);
    sale = saleRepo.save(sale);

    int subtotal = 0;
    for (SaleLineRequest lr : req.lines()) {
      Product prod =
          productRepo
              .findByBusinessIdAndId(p.businessId(), lr.productId())
              .orElseThrow(() -> new ResourceNotFoundException("Product", lr.productId()));
      SaleLine line =
          SaleLine.create(
              sale.getId(), prod.getId(), prod.getName(), lr.quantity(), prod.getSalePrice());
      lineRepo.save(line);
      subtotal += line.getLineTotal();
      stockService.updateStockForSale(
          prod.getId(), req.storeId(), p.userId(), lr.quantity(), sale.getId().toString());
    }

    sale.setSubtotal(subtotal);
    sale.setTotal(subtotal - req.discountAmount());
    if (req.amountReceived() != null) {
      sale.setAmountReceived(req.amountReceived());
      sale.setChangeGiven(Math.max(0, req.amountReceived() - sale.getTotal()));
    }
    if (sale.isCreditSale()) {
      Client c = clientRepo.findByBusinessIdAndId(p.businessId(), req.clientId()).orElseThrow();
      c.addCredit(sale.getTotal());
      clientRepo.save(c);
    }
    sale = saleRepo.save(sale);
    return mapSale(sale);
  }

  public SaleResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_READ);
    return mapSale(
        saleRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Sale", id)));
  }

  public Page<SaleResponse> list(
      UserPrincipal p,
      UUID storeId,
      Instant periodStart,
      Instant periodEnd,
      String status,
      Pageable pg) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_READ);
    Page<Sale> page =
        saleRepo.findFiltered(
            p.businessId(),
            storeId,
            status,
            periodStart,
            periodEnd,
            pg);
    return page.map(this::mapSale);
  }

  @Transactional
  public SaleResponse voidSale(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_DELETE);
    Sale sale =
        saleRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Sale", id));
    if (!sale.isCompleted()) throw new BusinessRuleException("Only completed sales can be voided");
    sale.markVoided();
    List<SaleLine> lines = lineRepo.findBySaleId(sale.getId());
    for (SaleLine line : lines)
      stockService.updateStockForPurchase(
          line.getProductId(),
          sale.getStoreId(),
          p.userId(),
          line.getQuantity(),
          "VOID-" + sale.getReceiptNumber());
    if (sale.isCreditSale()) {
      Client c = clientRepo.findByBusinessIdAndId(p.businessId(), sale.getClientId()).orElseThrow();
      c.deductCredit(sale.getTotal());
      clientRepo.save(c);
    }
    return mapSale(saleRepo.save(sale));
  }

  private String generateReceiptNumber() {
    String prefix =
        "RCP-"
            + DateTimeFormatter.ofPattern("yyyyMMdd")
                .format(Instant.now().atZone(ZoneId.systemDefault()));
    String num;
    do {
      num = prefix + "-" + String.format("%04d", (int) (Math.random() * 10000));
    } while (saleRepo.existsByReceiptNumber(num));
    return num;
  }

  private SaleResponse mapSale(Sale s) {
    List<SaleLineResponse> lines =
        lineRepo.findBySaleId(s.getId()).stream()
            .map(
                l ->
                    new SaleLineResponse(
                        l.getId(),
                        l.getProductId(),
                        l.getProductName(),
                        l.getQuantity(),
                        l.getUnitPrice(),
                        l.getLineTotal()))
            .toList();
    var store = storeRepo.findById(s.getStoreId()).orElse(null);
    String storeName = store != null ? store.getName() : "Boutique";
    String storeAddress = store != null ? store.getAddress() : null;
    return new SaleResponse(
        s.getId(),
        s.getBusinessId(),
        s.getStoreId(),
        storeName,
        storeAddress,
        s.getUserId(),
        s.getClientId(),
        s.getReceiptNumber(),
        s.getPaymentMethod(),
        s.getSubtotal(),
        s.getDiscountAmount(),
        s.getTotal(),
        s.getAmountReceived(),
        s.getChangeGiven(),
        s.getStatus(),
        s.getNote(),
        lines,
        s.getCreatedAt());
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
  }
}
