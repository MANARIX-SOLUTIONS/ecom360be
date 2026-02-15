package com.ecom360.supplier.application.service;

import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.inventory.application.service.StockService;
import com.ecom360.shared.domain.exception.*;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.supplier.application.dto.*;
import com.ecom360.supplier.domain.model.*;
import com.ecom360.supplier.domain.repository.*;
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
  private final SupplierRepository supplierRepo;
  private final ProductRepository productRepo;
  private final StoreRepository storeRepo;
  private final StockService stockService;

  public PurchaseOrderService(
      PurchaseOrderRepository poRepo,
      PurchaseOrderLineRepository lineRepo,
      SupplierRepository supplierRepo,
      ProductRepository productRepo,
      StoreRepository storeRepo,
      StockService stockService) {
    this.poRepo = poRepo;
    this.lineRepo = lineRepo;
    this.supplierRepo = supplierRepo;
    this.productRepo = productRepo;
    this.storeRepo = storeRepo;
    this.stockService = stockService;
  }

  @Transactional
  public PurchaseOrderResponse create(PurchaseOrderRequest r, UserPrincipal p) {
    requireBiz(p);
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
    return mapPO(
        poRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id)));
  }

  public Page<PurchaseOrderResponse> list(
      UserPrincipal p, String status, UUID supplierId, Pageable pg) {
    requireBiz(p);
    if (status != null)
      return poRepo
          .findByBusinessIdAndStatusOrderByCreatedAtDesc(p.businessId(), status, pg)
          .map(this::mapPO);
    if (supplierId != null)
      return poRepo
          .findByBusinessIdAndSupplierIdOrderByCreatedAtDesc(p.businessId(), supplierId, pg)
          .map(this::mapPO);
    return poRepo.findByBusinessIdOrderByCreatedAtDesc(p.businessId(), pg).map(this::mapPO);
  }

  @Transactional
  public PurchaseOrderResponse updateStatus(UUID id, String newStatus, UserPrincipal p) {
    requireBiz(p);
    PurchaseOrder po =
        poRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
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
      Supplier sup =
          supplierRepo.findByBusinessIdAndId(p.businessId(), po.getSupplierId()).orElseThrow();
      sup.addToBalance(po.getTotalAmount());
      supplierRepo.save(sup);
    }
    return mapPO(poRepo.save(po));
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
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
  }

  private PurchaseOrderResponse mapPO(PurchaseOrder po) {
    List<PurchaseOrderLineResponse> lines =
        lineRepo.findByPurchaseOrderId(po.getId()).stream()
            .map(
                l ->
                    new PurchaseOrderLineResponse(
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
        po.getExpectedDate(),
        po.getReceivedDate(),
        po.getNote(),
        lines,
        po.getCreatedAt(),
        po.getUpdatedAt());
  }
}
