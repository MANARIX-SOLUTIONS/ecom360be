package com.ecom360.sales.application.service;

import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.client.domain.ClientCreditPolicy;
import com.ecom360.client.domain.model.Client;
import com.ecom360.client.domain.repository.ClientRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.inventory.application.service.StockService;
import com.ecom360.notification.application.service.NotificationPublisher;
import com.ecom360.notification.application.service.NotificationTypes;
import com.ecom360.sales.application.dto.*;
import com.ecom360.sales.domain.SalePaymentPolicy;
import com.ecom360.sales.domain.model.*;
import com.ecom360.sales.domain.repository.*;
import com.ecom360.shared.domain.exception.*;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.domain.model.Plan;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
  private final SalePaymentRepository salePaymentRepo;
  private final ProductRepository productRepo;
  private final StoreRepository storeRepo;
  private final ClientRepository clientRepo;
  private final StockService stockService;
  private final SubscriptionService subscriptionService;
  private final RolePermissionService permissionService;
  private final NotificationPublisher notificationPublisher;

  public SaleService(
      SaleRepository saleRepo,
      SaleLineRepository lineRepo,
      SalePaymentRepository salePaymentRepo,
      ProductRepository productRepo,
      StoreRepository storeRepo,
      ClientRepository clientRepo,
      StockService stockService,
      SubscriptionService subscriptionService,
      RolePermissionService permissionService,
      NotificationPublisher notificationPublisher) {
    this.saleRepo = saleRepo;
    this.lineRepo = lineRepo;
    this.salePaymentRepo = salePaymentRepo;
    this.productRepo = productRepo;
    this.storeRepo = storeRepo;
    this.clientRepo = clientRepo;
    this.stockService = stockService;
    this.subscriptionService = subscriptionService;
    this.permissionService = permissionService;
    this.notificationPublisher = notificationPublisher;
  }

  @Transactional
  public SaleResponse createSale(SaleRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_CREATE);
    validateSubscriptionForSale(p.businessId(), req.paymentMethod());
    storeRepo
        .findById(req.storeId())
        .filter(s -> s.belongsTo(p.businessId()))
        .orElseThrow(() -> new ResourceNotFoundException("Store", req.storeId()));
    UUID clientId = requireClientForPosSale(
        req.clientId(), p.businessId(), req.paymentMethod());

    List<LineSpec> specs = new ArrayList<>();
    for (SaleLineRequest lr : req.lines()) {
      Product prod = productRepo
          .findByBusinessIdAndId(p.businessId(), lr.productId())
          .orElseThrow(() -> new ResourceNotFoundException("Product", lr.productId()));
      Integer salePv = prod.getSalePrice();
      if (salePv == null || salePv <= 0) {
        throw new BusinessRuleException(
            "Prix de vente manquant ou invalide pour le produit « " + prod.getName() + " ».");
      }
      specs.add(new LineSpec(prod.getId(), prod.getName(), lr.quantity(), salePv));
    }
    return persistSaleFromLineSpecs(
        p.businessId(),
        req.storeId(),
        p.userId(),
        clientId,
        req.paymentMethod(),
        req.discountAmount(),
        req.amountReceived(),
        req.amountPaid(),
        req.dueDate(),
        req.note(),
        specs);
  }

  /**
   * Création de vente depuis une intégration commerce (sans contrôle de
   * permission POS). Les lignes
   * portent les prix issus du site.
   */
  @Transactional
  public SaleResponse createSaleFromImport(
      UUID businessId,
      UUID storeId,
      UUID actingUserId,
      String paymentMethod,
      int discountAmount,
      String note,
      List<ImportedSaleLine> lines) {
    if (ClientCreditPolicy.isCreditPayment(paymentMethod)) {
      throw new BusinessRuleException(ClientCreditPolicy.IMPORT_CREDIT_FORBIDDEN);
    }
    validateSubscriptionForSale(businessId, paymentMethod);
    storeRepo
        .findById(storeId)
        .filter(s -> s.belongsTo(businessId))
        .orElseThrow(() -> new ResourceNotFoundException("Store", storeId));
    List<LineSpec> specs = new ArrayList<>();
    for (ImportedSaleLine il : lines) {
      Product prod = productRepo
          .findByBusinessIdAndId(businessId, il.productId())
          .orElseThrow(() -> new ResourceNotFoundException("Product", il.productId()));
      if (!prod.getStoreId().equals(storeId)) {
        throw new BusinessRuleException(
            "Le produit n'appartient pas à la boutique liée à cette connexion commerce.");
      }
      String lineName = il.lineLabel() != null && !il.lineLabel().isBlank() ? il.lineLabel() : prod.getName();
      specs.add(new LineSpec(prod.getId(), lineName, il.quantity(), il.unitPriceMinorUnits()));
    }
    return persistSaleFromLineSpecs(
        businessId,
        storeId,
        actingUserId,
        null,
        paymentMethod,
        discountAmount,
        null,
        null,
        null,
        note,
        specs);
  }

  private void validateSubscriptionForSale(UUID businessId, String paymentMethod) {
    subscriptionService
        .getPlanForBusiness(businessId)
        .ifPresent(
            plan -> {
              if (!plan.isUnlimited(plan.getMaxSalesPerMonth())) {
                ZoneId zone = ZoneId.systemDefault();
                LocalDate now = LocalDate.now(zone);
                Instant start = now.withDayOfMonth(1).atStartOfDay(zone).toInstant();
                Instant end = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant();
                long count = saleRepo.countByBusinessIdAndCreatedAtBetween(businessId, start, end);
                if (count >= plan.getMaxSalesPerMonth()) {
                  throw new BusinessRuleException(
                      "Limite du plan atteinte : maximum "
                          + plan.getMaxSalesPerMonth()
                          + " ventes/mois. Passez à un plan supérieur.");
                }
              }
              rejectUnsupportedPaymentMethods(plan, paymentMethod);
            });
  }

  /**
   * Wave / Orange Money / crédit — sans contrôle du quota mensuel de ventes
   * (réservé aux mises à jour).
   */
  private void validatePlanPaymentMethodsOnly(UUID businessId, String paymentMethod) {
    subscriptionService
        .getPlanForBusiness(businessId)
        .ifPresent(plan -> rejectUnsupportedPaymentMethods(plan, paymentMethod));
  }

  private void rejectUnsupportedPaymentMethods(Plan plan, String paymentMethod) {
    if (("wave".equals(paymentMethod) || "orange_money".equals(paymentMethod))
        && !Boolean.TRUE.equals(plan.getFeatureMultiPayment())) {
      throw new BusinessRuleException(
          "Paiement mobile (Wave, Orange Money) non inclus dans votre plan. Passez à un plan supérieur.");
    }
    if (ClientCreditPolicy.isCreditPayment(paymentMethod)) {
      ClientCreditPolicy.requireFeatureEnabled(plan.getFeatureClientCredits());
    }
  }

  private SaleResponse persistSaleFromLineSpecs(
      UUID businessId,
      UUID storeId,
      UUID userId,
      UUID clientId,
      String paymentMethod,
      int discountAmount,
      Integer amountReceived,
      Integer requestedAmountPaid,
      LocalDate dueDate,
      String note,
      List<LineSpec> lineSpecs) {
    Sale sale = new Sale();
    sale.setBusinessId(businessId);
    sale.setStoreId(storeId);
    sale.setUserId(userId);
    sale.setClientId(clientId);
    sale.setPaymentMethod(paymentMethod);
    sale.setDiscountAmount(discountAmount);
    sale.setDueDate(dueDate);
    sale.setNote(note);
    sale.setReceiptNumber(generateReceiptNumber());
    sale.setStatus("completed");
    sale.setSubtotal(0);
    sale.setTotal(0);
    sale.setAmountPaid(0);
    sale.setPaymentStatus(SalePaymentStatus.PAID);
    sale = saleRepo.save(sale);

    int subtotal = 0;
    for (LineSpec line : lineSpecs) {
      SaleLine saleLine = SaleLine.create(
          sale.getId(), line.productId(), line.lineName(), line.quantity(), line.unitPrice());
      lineRepo.save(saleLine);
      subtotal += saleLine.getLineTotal();
      stockService.updateStockForSale(
          line.productId(), storeId, userId, line.quantity(), sale.getId().toString());
    }

    if (discountAmount > subtotal) {
      throw new BusinessRuleException("La remise ne peut pas dépasser le sous-total.");
    }
    sale.setSubtotal(subtotal);
    sale.setTotal(subtotal - discountAmount);
    if (amountReceived != null) {
      sale.setAmountReceived(amountReceived);
      sale.setChangeGiven(Math.max(0, amountReceived - sale.getTotal()));
    }

    int amountPaid = resolveAmountPaid(requestedAmountPaid, paymentMethod, sale.getTotal());
    SalePaymentPolicy.requireValidDeposit(amountPaid, sale.getTotal());
    sale.setAmountPaid(amountPaid);
    sale.recomputePaymentStatus();

    int remaining = sale.getRemainingAmount();
    if (remaining > 0) {
      requireOutstandingAllowedByPlan(businessId);
      Client c = clientId == null
          ? null
          : clientRepo.findByBusinessIdAndId(businessId, clientId).orElse(null);
      SalePaymentPolicy.requireNamedClientForOutstanding(remaining, c);
      if (c == null) {
        throw new BusinessRuleException(SalePaymentPolicy.NAMED_CLIENT_REQUIRED);
      }
      c.addCredit(remaining);
      clientRepo.save(c);
    } else {
      sale.setDueDate(null);
    }

    sale = saleRepo.save(sale);
    if (amountPaid > 0) {
      salePaymentRepo.save(
          SalePayment.record(
              sale, userId, amountPaid, paymentMethod, SalePaymentKind.DEPOSIT, null));
    }
    notifyDigitalPaymentReceived(sale);
    return mapSale(sale);
  }

  /**
   * {@code null} conserve le comportement historique : tout est encaissé, sauf en
   * mode crédit où la vente part à zéro.
   */
  private int resolveAmountPaid(Integer requested, String paymentMethod, int total) {
    if (requested != null) {
      return requested;
    }
    return ClientCreditPolicy.isCreditPayment(paymentMethod) ? 0 : total;
  }

  /** Laisser un reste à payer revient à ouvrir une créance : même gating que le crédit. */
  private void requireOutstandingAllowedByPlan(UUID businessId) {
    subscriptionService
        .getPlanForBusiness(businessId)
        .ifPresent(
            plan -> ClientCreditPolicy.requireFeatureEnabled(plan.getFeatureClientCredits()));
  }

  private record LineSpec(UUID productId, String lineName, int quantity, int unitPrice) {
  }

  public SaleResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_READ);
    Sale sale = saleRepo
        .findByBusinessIdAndId(p.businessId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("Sale", id));
    if (subscriptionService.isBeforeDataRetention(p.businessId(), sale.getCreatedAt())) {
      throw new ResourceNotFoundException("Sale", id);
    }
    return mapSale(sale);
  }

  public Page<SaleResponse> list(
      UserPrincipal p,
      UUID storeId,
      Instant periodStart,
      Instant periodEnd,
      String status,
      String paymentStatus,
      UUID clientId,
      Pageable pg) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_READ);
    if (paymentStatus != null && !SalePaymentStatus.isValid(paymentStatus)) {
      throw new BusinessRuleException("Statut de paiement inconnu : " + paymentStatus);
    }
    Instant from = subscriptionService.clampSaleHistoryFrom(p.businessId(), periodStart);
    Page<Sale> page = saleRepo.findFiltered(
        p.businessId(), storeId, status, paymentStatus, clientId, from, periodEnd, pg);
    return page.map(this::mapSale);
  }

  /**
   * Met à jour une vente validée (lignes, remise, paiement, note). Le numéro de
   * reçu est conservé.
   * Ajuste le stock et le solde crédit client comme pour une annulation suivie
   * d'une nouvelle
   * vente.
   */
  @Transactional
  public SaleResponse updateSale(UUID id, SaleRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_UPDATE);
    Sale sale = saleRepo
        .findByBusinessIdAndId(p.businessId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("Sale", id));
    if (subscriptionService.isBeforeDataRetention(p.businessId(), sale.getCreatedAt())) {
      throw new BusinessRuleException(
          "Cette vente est hors de la période d'historique de votre plan.");
    }
    if (!sale.isCompleted()) {
      throw new BusinessRuleException("Seules les ventes validées peuvent être modifiées.");
    }
    if (!sale.getStoreId().equals(req.storeId())) {
      throw new BusinessRuleException("La boutique de la vente ne peut pas être changée.");
    }
    storeRepo
        .findById(req.storeId())
        .filter(s -> s.belongsTo(p.businessId()))
        .orElseThrow(() -> new ResourceNotFoundException("Store", req.storeId()));
    UUID clientId = requireClientForPosSale(
        req.clientId(), p.businessId(), req.paymentMethod());
    validatePlanPaymentMethodsOnly(p.businessId(), req.paymentMethod());

    List<SaleLine> oldLines = lineRepo.findBySaleId(sale.getId());
    for (SaleLine line : oldLines) {
      stockService.updateStockForPurchase(
          line.getProductId(),
          sale.getStoreId(),
          p.userId(),
          line.getQuantity(),
          "EDIT-REV-" + sale.getReceiptNumber());
    }
    int previousRemaining = sale.getRemainingAmount();
    if (previousRemaining > 0 && sale.getClientId() != null) {
      Sale finalSale = sale;
      Client oldClient = clientRepo
          .findByBusinessIdAndId(p.businessId(), sale.getClientId())
          .orElseThrow(() -> new ResourceNotFoundException("Client", finalSale.getClientId()));
      oldClient.deductCredit(previousRemaining);
      clientRepo.save(oldClient);
    }

    lineRepo.deleteAll(oldLines);

    List<LineSpec> specs = new ArrayList<>();
    for (SaleLineRequest lr : req.lines()) {
      Product prod = productRepo
          .findByBusinessIdAndId(p.businessId(), lr.productId())
          .orElseThrow(() -> new ResourceNotFoundException("Product", lr.productId()));
      Integer salePv = prod.getSalePrice();
      if (salePv == null || salePv <= 0) {
        throw new BusinessRuleException(
            "Prix de vente manquant ou invalide pour le produit « " + prod.getName() + " ».");
      }
      specs.add(new LineSpec(prod.getId(), prod.getName(), lr.quantity(), salePv));
    }

    int subtotal = 0;
    for (LineSpec line : specs) {
      SaleLine saleLine = SaleLine.create(
          sale.getId(), line.productId(), line.lineName(), line.quantity(), line.unitPrice());
      lineRepo.save(saleLine);
      subtotal += saleLine.getLineTotal();
      stockService.updateStockForSale(
          line.productId(),
          sale.getStoreId(),
          p.userId(),
          line.quantity(),
          sale.getId().toString());
    }

    int discountAmount = req.discountAmount() != null ? req.discountAmount() : 0;
    if (discountAmount > subtotal) {
      throw new BusinessRuleException("La remise ne peut pas dépasser le sous-total.");
    }
    int newTotal = subtotal - discountAmount;

    int alreadyCollected = sale.getAmountPaid() == null ? 0 : sale.getAmountPaid();
    SalePaymentPolicy.requireTotalCoversPaid(newTotal, alreadyCollected);
    int targetAmountPaid = resolveAmountPaid(req.amountPaid(), req.paymentMethod(), newTotal);
    SalePaymentPolicy.requireValidDeposit(targetAmountPaid, newTotal);
    if (targetAmountPaid < alreadyCollected) {
      throw new BusinessRuleException(SalePaymentPolicy.PAID_EXCEEDS_NEW_TOTAL);
    }

    sale.setClientId(clientId);
    sale.setPaymentMethod(req.paymentMethod());
    sale.setDiscountAmount(discountAmount);
    sale.setSubtotal(subtotal);
    sale.setTotal(newTotal);
    sale.setDueDate(req.dueDate());
    sale.setNote(req.note());
    if (req.amountReceived() != null) {
      sale.setAmountReceived(req.amountReceived());
      sale.setChangeGiven(Math.max(0, req.amountReceived() - newTotal));
    } else {
      sale.setAmountReceived(null);
      sale.setChangeGiven(null);
    }
    sale.setAmountPaid(targetAmountPaid);
    sale.recomputePaymentStatus();

    int newRemaining = sale.getRemainingAmount();
    if (newRemaining > 0) {
      requireOutstandingAllowedByPlan(p.businessId());
      Client c = clientRepo
          .findByBusinessIdAndId(p.businessId(), clientId)
          .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));
      SalePaymentPolicy.requireNamedClientForOutstanding(newRemaining, c);
      c.addCredit(newRemaining);
      clientRepo.save(c);
    } else {
      sale.setDueDate(null);
    }

    sale = saleRepo.save(sale);
    int complement = targetAmountPaid - alreadyCollected;
    if (complement > 0) {
      salePaymentRepo.save(
          SalePayment.record(
              sale,
              p.userId(),
              complement,
              req.paymentMethod(),
              SalePaymentKind.INSTALLMENT,
              "Complément suite à la modification de la vente"));
    }
    return mapSale(sale);
  }

  private UUID requireClientForPosSale(
      UUID clientId, UUID businessId, String paymentMethod) {
    if (clientId == null) {
      throw new BusinessRuleException("Client obligatoire pour cette vente.");
    }
    Client client = clientRepo
        .findByBusinessIdAndId(businessId, clientId)
        .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));
    ClientCreditPolicy.requireNamedClientForCredit(paymentMethod, client);
    return clientId;
  }

  @Transactional
  public SaleResponse voidSale(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_DELETE);
    Sale sale = saleRepo
        .findByBusinessIdAndId(p.businessId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("Sale", id));
    if (subscriptionService.isBeforeDataRetention(p.businessId(), sale.getCreatedAt())) {
      throw new BusinessRuleException(
          "Cette vente est hors de la période d'historique de votre plan.");
    }
    if (!sale.isCompleted())
      throw new BusinessRuleException("Seules les ventes validées peuvent être annulées.");
    sale.markVoided();
    List<SaleLine> lines = lineRepo.findBySaleId(sale.getId());
    for (SaleLine line : lines)
      stockService.updateStockForPurchase(
          line.getProductId(),
          sale.getStoreId(),
          p.userId(),
          line.getQuantity(),
          "VOID-" + sale.getReceiptNumber());
    int remaining = sale.getRemainingAmount();
    if (remaining > 0 && sale.getClientId() != null) {
      Client c = clientRepo
          .findByBusinessIdAndId(p.businessId(), sale.getClientId())
          .orElseThrow(() -> new ResourceNotFoundException("Client", sale.getClientId()));
      c.deductCredit(remaining);
      clientRepo.save(c);
    }
    return mapSale(saleRepo.save(sale));
  }

  /**
   * Encaisse un versement sur le solde d'une vente. Non conditionné au plan : un
   * commerçant doit pouvoir recouvrer ses créances même après une rétrogradation.
   */
  @Transactional
  public SalePaymentResponse recordPayment(UUID saleId, SalePaymentRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_UPDATE);
    Sale sale = saleRepo
        .findByBusinessIdAndId(p.businessId(), saleId)
        .orElseThrow(() -> new ResourceNotFoundException("Sale", saleId));
    if (subscriptionService.isBeforeDataRetention(p.businessId(), sale.getCreatedAt())) {
      throw new BusinessRuleException(
          "Cette vente est hors de la période d'historique de votre plan.");
    }
    SalePaymentPolicy.requirePayment(sale, req.amount());

    sale.applyPayment(req.amount());
    if (!sale.hasOutstandingBalance()) {
      sale.setDueDate(null);
    }
    sale = saleRepo.save(sale);

    UUID payerId = sale.getClientId();
    if (payerId != null) {
      Client c = clientRepo
          .findByBusinessIdAndId(p.businessId(), payerId)
          .orElseThrow(() -> new ResourceNotFoundException("Client", payerId));
      c.deductCredit(req.amount());
      clientRepo.save(c);
    }

    SalePayment payment = salePaymentRepo.save(
        SalePayment.record(
            sale,
            p.userId(),
            req.amount(),
            req.paymentMethod(),
            SalePaymentKind.INSTALLMENT,
            req.note()));
    return mapPayment(payment);
  }

  public List<SalePaymentResponse> listPayments(UUID saleId, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SALES_READ);
    Sale sale = saleRepo
        .findByBusinessIdAndId(p.businessId(), saleId)
        .orElseThrow(() -> new ResourceNotFoundException("Sale", saleId));
    return salePaymentRepo.findBySaleIdOrderByCreatedAtAsc(sale.getId()).stream()
        .map(this::mapPayment)
        .toList();
  }

  private SalePaymentResponse mapPayment(SalePayment sp) {
    return new SalePaymentResponse(
        sp.getId(),
        sp.getSaleId(),
        sp.getStoreId(),
        sp.getUserId(),
        sp.getAmount(),
        sp.getPaymentMethod(),
        sp.getKind(),
        sp.getNote(),
        sp.getCreatedAt());
  }

  private String generateReceiptNumber() {
    String prefix = "RCP-"
        + DateTimeFormatter.ofPattern("yyyyMMdd")
            .format(Instant.now().atZone(ZoneId.systemDefault()));
    String num;
    do {
      num = prefix + "-" + String.format("%04d", (int) (Math.random() * 10000));
    } while (saleRepo.existsByReceiptNumber(num));
    return num;
  }

  private SaleResponse mapSale(Sale s) {
    List<SaleLineResponse> lines = lineRepo.findBySaleId(s.getId()).stream()
        .map(
            l -> new SaleLineResponse(
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
        s.getAmountPaid(),
        s.getRemainingAmount(),
        s.getPaymentStatus(),
        s.getDueDate(),
        s.getAmountReceived(),
        s.getChangeGiven(),
        s.getStatus(),
        s.getNote(),
        lines,
        s.getCreatedAt());
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess())
      throw new AccessDeniedException("Business context required");
  }

  /**
   * In-app only, P / G. Hook for T1-04 {@code confirmPaid} when the sale
   * becomes {@code completed} after a Wave / OM intent.
   */
  void notifyDigitalPaymentReceived(Sale sale) {
    if (sale == null || !sale.isCompleted()) {
      return;
    }
    String method = sale.getPaymentMethod();
    if (!"wave".equals(method) && !"orange_money".equals(method)) {
      return;
    }
    String label = "wave".equals(method) ? "Wave" : "Orange Money";
    Integer total = sale.getTotal() != null ? sale.getTotal() : 0;
    notificationPublisher.notifyOwnersAndManagers(
        sale.getBusinessId(),
        NotificationTypes.PAYMENT_RECEIVED,
        "Paiement " + label + " reçu",
        total + " FCFA — reçu " + sale.getReceiptNumber() + ".",
        "/sales?saleId=" + sale.getId());
  }
}
