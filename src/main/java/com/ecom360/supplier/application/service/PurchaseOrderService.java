package com.ecom360.supplier.application.service;

import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.inventory.application.service.StockService;
import com.ecom360.shared.domain.exception.*;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.supplier.application.dto.*;
import com.ecom360.supplier.domain.PurchaseOrderPaymentPolicy;
import com.ecom360.supplier.domain.model.*;
import com.ecom360.supplier.domain.repository.*;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderService {
  private final PurchaseOrderRepository poRepo;
  private final PurchaseOrderLineRepository lineRepo;
  private final PurchaseOrderPaymentRepository poPaymentRepo;
  private final SupplierRepository supplierRepo;
  private final ProductRepository productRepo;
  private final StoreRepository storeRepo;
  private final StockService stockService;
  private final RolePermissionService permissionService;
  private final SubscriptionService subscriptionService;

  public PurchaseOrderService(
      PurchaseOrderRepository poRepo,
      PurchaseOrderLineRepository lineRepo,
      PurchaseOrderPaymentRepository poPaymentRepo,
      SupplierRepository supplierRepo,
      ProductRepository productRepo,
      StoreRepository storeRepo,
      StockService stockService,
      RolePermissionService permissionService,
      SubscriptionService subscriptionService) {
    this.poRepo = poRepo;
    this.lineRepo = lineRepo;
    this.poPaymentRepo = poPaymentRepo;
    this.supplierRepo = supplierRepo;
    this.productRepo = productRepo;
    this.storeRepo = storeRepo;
    this.stockService = stockService;
    this.permissionService = permissionService;
    this.subscriptionService = subscriptionService;
  }

  private void requireSupplierTracking(UserPrincipal p) {
    subscriptionService.requireSupplierTracking(p.businessId());
  }

  @Transactional
  public PurchaseOrderResponse create(PurchaseOrderRequest r, UserPrincipal p) {
    requireBiz(p);
    requireSupplierTracking(p);
    permissionService.require(p, Permission.PURCHASE_ORDERS_CREATE);
    supplierRepo
        .findByBusinessIdAndId(p.businessId(), r.supplierId())
        .orElseThrow(() -> new ResourceNotFoundException("Supplier", r.supplierId()));
    storeRepo
        .findById(r.storeId())
        .filter(s -> s.belongsTo(p.businessId()))
        .orElseThrow(() -> new ResourceNotFoundException("Store", r.storeId()));

    PurchaseOrder po = new PurchaseOrder();
    po.setBusinessId(p.businessId());
    po.setSupplierId(r.supplierId());
    po.setStoreId(r.storeId());
    po.setUserId(p.userId());
    po.setReference(genRef(p.businessId()));
    po.setStatus("draft");
    po.setExpectedDate(r.expectedDate());
    po.setNote(r.note());
    po.setTotalAmount(0);
    po.setAmountPaid(0);
    po.setPaymentStatus(PurchaseOrderPaymentStatus.UNPAID);
    po = poRepo.save(po);

    int total = 0;
    for (PurchaseOrderLineRequest lr : r.lines()) {
      productRepo
          .findByBusinessIdAndId(p.businessId(), lr.productId())
          .orElseThrow(() -> new ResourceNotFoundException("Product", lr.productId()));
      PurchaseOrderLine line = new PurchaseOrderLine();
      line.setPurchaseOrderId(po.getId());
      line.setProductId(lr.productId());
      line.setQuantity(lr.quantity());
      line.setUnitCost(lr.unitCost());
      line.setLineTotal(lr.quantity() * lr.unitCost());
      lineRepo.save(line);
      total += line.getLineTotal();
    }
    po.setTotalAmount(total);
    po = poRepo.save(po);
    return mapPO(po);
  }

  public PurchaseOrderResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    requireSupplierTracking(p);
    permissionService.require(p, Permission.PURCHASE_ORDERS_READ);
    return mapPO(
        poRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id)));
  }

  public Page<PurchaseOrderResponse> list(
      UserPrincipal p, String status, UUID supplierId, Pageable pg) {
    requireBiz(p);
    requireSupplierTracking(p);
    permissionService.require(p, Permission.PURCHASE_ORDERS_READ);
    if (status != null && supplierId != null) {
      return poRepo
          .findByBusinessIdAndStatusAndSupplierIdOrderByCreatedAtDesc(
              p.businessId(), status, supplierId, pg)
          .map(this::mapPO);
    }
    if (status != null) {
      return poRepo
          .findByBusinessIdAndStatusOrderByCreatedAtDesc(p.businessId(), status, pg)
          .map(this::mapPO);
    }
    if (supplierId != null) {
      return poRepo
          .findByBusinessIdAndSupplierIdOrderByCreatedAtDesc(p.businessId(), supplierId, pg)
          .map(this::mapPO);
    }
    return poRepo.findByBusinessIdOrderByCreatedAtDesc(p.businessId(), pg).map(this::mapPO);
  }

  @Transactional
  public PurchaseOrderResponse updateStatus(
      UUID id, PurchaseOrderStatusUpdateRequest req, UserPrincipal p) {
    requireBiz(p);
    requireSupplierTracking(p);
    permissionService.require(p, Permission.PURCHASE_ORDERS_UPDATE);
    PurchaseOrder po = poRepo
        .findByBusinessIdAndIdForUpdate(p.businessId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
    String newStatus = req.status();
    po.transitionTo(newStatus);
    if ("received".equals(newStatus)) {
      List<PurchaseOrderLine> lines = lineRepo.findByPurchaseOrderId(po.getId());
      for (PurchaseOrderLine line : lines)
        stockService.updateStockForPurchase(
            line.getProductId(),
            po.getStoreId(),
            p.userId(),
            line.getQuantity(),
            po.getReference());
      // Acompte omis (null) ou 0 : dette égale au total, comportement historique.
      int amountPaid = req.amountPaid() == null ? 0 : req.amountPaid();
      PurchaseOrderPaymentPolicy.requireValidDeposit(amountPaid, po.getTotalAmount());
      po.setAmountPaid(amountPaid);
      po.recomputePaymentStatus();
      po.setDueDate(amountPaid < po.getTotalAmount() ? req.dueDate() : null);

      Supplier sup = supplierRepo
          .findByBusinessIdAndId(p.businessId(), po.getSupplierId())
          .orElseThrow(() -> new ResourceNotFoundException("Supplier", po.getSupplierId()));
      sup.addToBalance(po.getRemainingAmount());
      supplierRepo.save(sup);

      if (amountPaid > 0) {
        String method = req.paymentMethod() != null ? req.paymentMethod() : "cash";
        poPaymentRepo.save(
            PurchaseOrderPayment.record(
                po, p.userId(), amountPaid, method, PurchaseOrderPaymentKind.DEPOSIT, null));
      }
    }
    return mapPO(poRepo.save(po));
  }

  /**
   * Règle un versement sur le solde d'un bon réceptionné. Déduit le solde
   * fournisseur sans créer de {@code SupplierPayment} (même logique que vente vs
   * client_payment).
   */
  @Transactional
  public PurchaseOrderPaymentResponse recordPayment(
      UUID poId, PurchaseOrderPaymentRequest req, UserPrincipal p) {
    requireBiz(p);
    requireSupplierTracking(p);
    permissionService.require(p, Permission.PURCHASE_ORDERS_UPDATE);
    PurchaseOrder po = poRepo
        .findByBusinessIdAndIdForUpdate(p.businessId(), poId)
        .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", poId));
    PurchaseOrderPaymentPolicy.requirePayment(po, req.amount());

    po.applyPayment(req.amount());
    if (!po.hasOutstandingBalance()) {
      po.setDueDate(null);
    }
    poRepo.save(po);

    Supplier sup = supplierRepo
        .findByBusinessIdAndId(p.businessId(), po.getSupplierId())
        .orElseThrow(() -> new ResourceNotFoundException("Supplier", po.getSupplierId()));
    sup.deductFromBalance(req.amount());
    supplierRepo.save(sup);

    PurchaseOrderPayment payment = poPaymentRepo.save(
        PurchaseOrderPayment.record(
            po,
            p.userId(),
            req.amount(),
            req.paymentMethod(),
            PurchaseOrderPaymentKind.INSTALLMENT,
            req.note()));
    return mapPayment(payment);
  }

  public List<PurchaseOrderPaymentResponse> listPayments(UUID poId, UserPrincipal p) {
    requireBiz(p);
    requireSupplierTracking(p);
    permissionService.require(p, Permission.PURCHASE_ORDERS_READ);
    PurchaseOrder po = poRepo
        .findByBusinessIdAndId(p.businessId(), poId)
        .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", poId));
    return poPaymentRepo.findByPurchaseOrderIdOrderByCreatedAtAsc(po.getId()).stream()
        .map(this::mapPayment)
        .toList();
  }

  private String genRef(UUID bizId) {
    long c = poRepo.countByBusinessId(bizId) + 1;
    String ref;
    do {
      ref = "PO-" + String.format("%05d", c++);
    } while (poRepo.existsByReference(ref));
    return ref;
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess())
      throw new AccessDeniedException("Business context required");
  }

  private PurchaseOrderResponse mapPO(PurchaseOrder po) {
    List<PurchaseOrderLineResponse> lines = lineRepo.findByPurchaseOrderId(po.getId()).stream()
        .map(
            l -> new PurchaseOrderLineResponse(
                l.getId(),
                l.getProductId(),
                l.getQuantity(),
                l.getUnitCost(),
                l.getLineTotal()))
        .toList();
    return new PurchaseOrderResponse(
        po.getId(),
        po.getBusinessId(),
        po.getSupplierId(),
        po.getStoreId(),
        po.getUserId(),
        po.getReference(),
        po.getStatus(),
        po.getTotalAmount(),
        po.getAmountPaid() == null ? 0 : po.getAmountPaid(),
        po.getRemainingAmount(),
        po.getPaymentStatus(),
        po.getDueDate(),
        po.getExpectedDate(),
        po.getReceivedDate(),
        po.getNote(),
        lines,
        po.getCreatedAt(),
        po.getUpdatedAt());
  }

  private PurchaseOrderPaymentResponse mapPayment(PurchaseOrderPayment p) {
    return new PurchaseOrderPaymentResponse(
        p.getId(),
        p.getPurchaseOrderId(),
        p.getStoreId(),
        p.getUserId(),
        p.getAmount(),
        p.getPaymentMethod(),
        p.getKind(),
        p.getNote(),
        p.getCreatedAt());
  }
}
