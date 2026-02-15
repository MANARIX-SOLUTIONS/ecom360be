package com.ecom360.inventory.application.service;

import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.inventory.application.dto.*;
import com.ecom360.inventory.domain.model.*;
import com.ecom360.inventory.domain.repository.*;
import com.ecom360.shared.domain.exception.*;
import com.ecom360.store.domain.model.Store;
import com.ecom360.store.domain.repository.StoreRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class StockService {
    private final ProductStoreStockRepository stockRepo;
    private final StockMovementRepository movementRepo;
    private final ProductRepository productRepo;
    private final StoreRepository storeRepo;

    public StockService(ProductStoreStockRepository stockRepo, StockMovementRepository movementRepo, ProductRepository productRepo, StoreRepository storeRepo) {
        this.stockRepo = stockRepo; this.movementRepo = movementRepo; this.productRepo = productRepo; this.storeRepo = storeRepo;
    }

    @Transactional
    public StockLevelResponse initializeStock(StockInitRequest r, UserPrincipal p) {
        requireBiz(p); verifyProduct(r.productId(), p.businessId()); verifyStore(r.storeId(), p.businessId());
        if (stockRepo.existsByProductIdAndStoreId(r.productId(), r.storeId())) throw new BusinessRuleException("Stock already initialized for this product/store");
        ProductStoreStock s = new ProductStoreStock(); s.setProductId(r.productId()); s.setStoreId(r.storeId()); s.setQuantity(r.quantity()); s.setMinStock(r.minStock());
        s = stockRepo.save(s);
        if (r.quantity() > 0) movementRepo.save(StockMovement.record(r.productId(), r.storeId(), p.userId(), "in", r.quantity(), 0, r.quantity(), null, "Initial stock"));
        return mapLevel(s);
    }

    @Transactional
    public StockMovementResponse adjustStock(StockAdjustmentRequest r, UserPrincipal p) {
        requireBiz(p); verifyProduct(r.productId(), p.businessId()); verifyStore(r.storeId(), p.businessId());
        ProductStoreStock s = stockRepo.findByProductIdAndStoreId(r.productId(), r.storeId()).orElseThrow(() -> new ResourceNotFoundException("Stock not initialized"));
        int before = s.getQuantity();
        int delta = switch (r.type()) { case "in" -> Math.abs(r.quantity()); case "out" -> -Math.abs(r.quantity()); case "adjustment" -> r.quantity(); default -> throw new BusinessRuleException("Invalid type"); };
        if (before + delta < 0) throw new BusinessRuleException("Insufficient stock");
        s.adjustQuantity(delta); stockRepo.save(s);
        StockMovement m = movementRepo.save(StockMovement.record(r.productId(), r.storeId(), p.userId(), r.type(), delta, before, s.getQuantity(), r.reference(), r.note()));
        return mapMov(m);
    }

    public List<StockLevelResponse> getStockByStore(UUID storeId, UserPrincipal p) { requireBiz(p); verifyStore(storeId, p.businessId()); return stockRepo.findByStoreId(storeId).stream().map(this::mapLevel).toList(); }
    public StockLevelResponse getStockLevel(UUID productId, UUID storeId, UserPrincipal p) { requireBiz(p); return mapLevel(stockRepo.findByProductIdAndStoreId(productId, storeId).orElseThrow(() -> new ResourceNotFoundException("Stock not found"))); }
    public Page<StockMovementResponse> getMovements(UUID productId, UUID storeId, UserPrincipal p, Pageable pg) { requireBiz(p); return movementRepo.findByProductIdAndStoreIdOrderByCreatedAtDesc(productId, storeId, pg).map(this::mapMov); }
    public Page<StockMovementResponse> getMovementsByStore(UUID storeId, UserPrincipal p, Pageable pg) { requireBiz(p); verifyStore(storeId, p.businessId()); return movementRepo.findByStoreIdOrderByCreatedAtDesc(storeId, pg).map(this::mapMov); }

    @Transactional
    public void updateStockForSale(UUID productId, UUID storeId, UUID userId, int qty, String saleId) {
        ProductStoreStock s = stockRepo.findByProductIdAndStoreId(productId, storeId).orElseGet(() -> { ProductStoreStock n = new ProductStoreStock(); n.setProductId(productId); n.setStoreId(storeId); n.setQuantity(0); n.setMinStock(0); return stockRepo.save(n); });
        int before = s.getQuantity(); s.adjustQuantity(-qty); stockRepo.save(s);
        movementRepo.save(StockMovement.record(productId, storeId, userId, "sale", -qty, before, s.getQuantity(), saleId, null));
    }

    @Transactional
    public void updateStockForPurchase(UUID productId, UUID storeId, UUID userId, int qty, String ref) {
        ProductStoreStock s = stockRepo.findByProductIdAndStoreId(productId, storeId).orElseGet(() -> { ProductStoreStock n = new ProductStoreStock(); n.setProductId(productId); n.setStoreId(storeId); n.setQuantity(0); n.setMinStock(0); return stockRepo.save(n); });
        int before = s.getQuantity(); s.adjustQuantity(qty); stockRepo.save(s);
        movementRepo.save(StockMovement.record(productId, storeId, userId, "in", qty, before, s.getQuantity(), ref, "Purchase order received"));
    }

    private void verifyProduct(UUID id, UUID bizId) { productRepo.findByBusinessIdAndId(bizId, id).orElseThrow(() -> new ResourceNotFoundException("Product", id)); }
    private void verifyStore(UUID id, UUID bizId) { storeRepo.findById(id).filter(s -> s.belongsTo(bizId)).orElseThrow(() -> new ResourceNotFoundException("Store", id)); }
    private void requireBiz(UserPrincipal p) { if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required"); }
    private StockLevelResponse mapLevel(ProductStoreStock s) {
        Product pr = productRepo.findById(s.getProductId()).orElse(null); Store st = storeRepo.findById(s.getStoreId()).orElse(null);
        return new StockLevelResponse(s.getId(), s.getProductId(), pr!=null?pr.getName():null, s.getStoreId(), st!=null?st.getName():null, s.getQuantity(), s.getMinStock(), s.isLowStock(), s.getUpdatedAt());
    }
    private StockMovementResponse mapMov(StockMovement m) { return new StockMovementResponse(m.getId(), m.getProductId(), m.getStoreId(), m.getUserId(), m.getType(), m.getQuantity(), m.getQuantityBefore(), m.getQuantityAfter(), m.getReference(), m.getNote(), m.getCreatedAt()); }
}
