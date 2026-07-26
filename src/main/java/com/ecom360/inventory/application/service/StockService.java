package com.ecom360.inventory.application.service;

import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.inventory.application.dto.*;
import com.ecom360.inventory.domain.model.*;
import com.ecom360.inventory.domain.repository.*;
import com.ecom360.shared.domain.exception.*;
import com.ecom360.store.domain.model.Store;
import com.ecom360.store.domain.repository.StoreRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {
  private final ProductStoreStockRepository stockRepo;
  private final StockMovementRepository movementRepo;
  private final ProductRepository productRepo;
  private final StoreRepository storeRepo;
  private final RolePermissionService permissionService;

  public StockService(
      ProductStoreStockRepository stockRepo,
      StockMovementRepository movementRepo,
      ProductRepository productRepo,
      StoreRepository storeRepo,
      RolePermissionService permissionService) {
    this.stockRepo = stockRepo;
    this.movementRepo = movementRepo;
    this.productRepo = productRepo;
    this.storeRepo = storeRepo;
    this.permissionService = permissionService;
  }

  @Transactional
  public StockLevelResponse initializeStock(StockInitRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STOCK_INIT);
    verifyProduct(r.productId(), p.businessId());
    verifyStore(r.storeId(), p.businessId());
    if (stockRepo.existsByProductIdAndStoreId(r.productId(), r.storeId()))
      throw new BusinessRuleException("Stock already initialized for this product/store");
    ProductStoreStock s = new ProductStoreStock();
    s.setProductId(r.productId());
    s.setStoreId(r.storeId());
    s.setQuantity(r.quantity());
    s.setMinStock(r.minStock());
    s = stockRepo.save(s);
    if (r.quantity() > 0)
      movementRepo.save(
          StockMovement.record(
              r.productId(),
              r.storeId(),
              p.userId(),
              "in",
              r.quantity(),
              0,
              r.quantity(),
              null,
              "Initial stock"));
    return mapLevel(s);
  }

  @Transactional
  public StockMovementResponse adjustStock(StockAdjustmentRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STOCK_ADJUST);
    verifyProduct(r.productId(), p.businessId());
    verifyStore(r.storeId(), p.businessId());
    ProductStoreStock s = stockRepo
        .findByProductIdAndStoreId(r.productId(), r.storeId())
        .orElseGet(
            () -> {
              if (!"adjustment".equals(r.type()))
                throw new ResourceNotFoundException("Stock not initialized");
              ProductStoreStock n = new ProductStoreStock();
              n.setProductId(r.productId());
              n.setStoreId(r.storeId());
              n.setQuantity(0);
              n.setMinStock(0);
              return stockRepo.save(n);
            });
    int before = s.getQuantity();
    int delta = switch (r.type()) {
      case "in" -> Math.abs(r.quantity());
      case "out" -> -Math.abs(r.quantity());
      case "adjustment" -> {
        if (r.quantity() < 0)
          throw new BusinessRuleException("Quantity cannot be negative");
        yield r.quantity() - before;
      }
      default -> throw new BusinessRuleException("Invalid type");
    };
    if (before + delta < 0)
      throw new BusinessRuleException("Insufficient stock");
    s.adjustQuantity(delta);
    stockRepo.save(s);
    StockMovement m = movementRepo.save(
        StockMovement.record(
            r.productId(),
            r.storeId(),
            p.userId(),
            r.type(),
            delta,
            before,
            s.getQuantity(),
            r.reference(),
            r.note()));
    return mapMov(m);
  }

  public Page<StockLevelResponse> getStockByStore(
      UUID storeId, String search, UserPrincipal p, Pageable pg) {
    requireBiz(p);
    permissionService.require(p, Permission.STOCK_READ);
    verifyStore(storeId, p.businessId());
    Page<ProductStoreStock> page = search != null && !search.isBlank()
        ? stockRepo.searchByStoreId(storeId, search.trim(), pg)
        : stockRepo.findByStoreId(storeId, pg);
    return mapLevels(page);
  }

  /** Stock levels for a set of products in a store (products list page). */
  public List<StockLevelResponse> getStockByStoreAndProducts(
      UUID storeId, Collection<UUID> productIds, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STOCK_READ);
    verifyStore(storeId, p.businessId());
    if (productIds == null || productIds.isEmpty()) {
      return List.of();
    }
    return mapLevels(stockRepo.findByStoreIdAndProductIdIn(storeId, productIds));
  }

  public StockLevelResponse getStockLevel(UUID productId, UUID storeId, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STOCK_READ);
    verifyProduct(productId, p.businessId());
    verifyStore(storeId, p.businessId());
    return mapLevel(
        stockRepo
            .findByProductIdAndStoreId(productId, storeId)
            .orElseThrow(() -> new ResourceNotFoundException("Stock not found")));
  }

  public Page<StockMovementResponse> getMovements(
      UUID productId, UUID storeId, UserPrincipal p, Pageable pg) {
    requireBiz(p);
    permissionService.require(p, Permission.STOCK_READ);
    verifyProduct(productId, p.businessId());
    verifyStore(storeId, p.businessId());
    return movementRepo
        .findByProductIdAndStoreIdOrderByCreatedAtDesc(productId, storeId, pg)
        .map(this::mapMov);
  }

  public Page<StockMovementResponse> getMovementsByStore(
      UUID storeId, UserPrincipal p, Pageable pg) {
    requireBiz(p);
    permissionService.require(p, Permission.STOCK_READ);
    verifyStore(storeId, p.businessId());
    return movementRepo.findByStoreIdOrderByCreatedAtDesc(storeId, pg).map(this::mapMov);
  }

  @Transactional
  public void updateStockForSale(
      UUID productId, UUID storeId, UUID userId, int qty, String saleId) {
    ProductStoreStock s = stockRepo
        .findByProductIdAndStoreId(productId, storeId)
        .orElseGet(
            () -> {
              ProductStoreStock n = new ProductStoreStock();
              n.setProductId(productId);
              n.setStoreId(storeId);
              n.setQuantity(0);
              n.setMinStock(0);
              return stockRepo.save(n);
            });
    int before = s.getQuantity();
    if (before - qty < 0) {
      throw new BusinessRuleException(
          "Stock insuffisant pour ce produit (disponible: " + before + ", demandé: " + qty + ")");
    }
    s.adjustQuantity(-qty);
    stockRepo.save(s);
    movementRepo.save(
        StockMovement.record(
            productId, storeId, userId, "sale", -qty, before, s.getQuantity(), saleId, null));
  }

  @Transactional
  public void updateStockForPurchase(
      UUID productId, UUID storeId, UUID userId, int qty, String ref) {
    ProductStoreStock s = stockRepo
        .findByProductIdAndStoreId(productId, storeId)
        .orElseGet(
            () -> {
              ProductStoreStock n = new ProductStoreStock();
              n.setProductId(productId);
              n.setStoreId(storeId);
              n.setQuantity(0);
              n.setMinStock(0);
              return stockRepo.save(n);
            });
    int before = s.getQuantity();
    s.adjustQuantity(qty);
    stockRepo.save(s);
    movementRepo.save(
        StockMovement.record(
            productId,
            storeId,
            userId,
            "in",
            qty,
            before,
            s.getQuantity(),
            ref,
            "Purchase order received"));
  }

  private void verifyProduct(UUID id, UUID bizId) {
    productRepo
        .findByBusinessIdAndId(bizId, id)
        .orElseThrow(() -> new ResourceNotFoundException("Product", id));
  }

  private void verifyStore(UUID id, UUID bizId) {
    storeRepo
        .findById(id)
        .filter(s -> s.belongsTo(bizId))
        .orElseThrow(() -> new ResourceNotFoundException("Store", id));
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess())
      throw new AccessDeniedException("Business context required");
  }

  private Page<StockLevelResponse> mapLevels(Page<ProductStoreStock> page) {
    if (page.isEmpty()) {
      return page.map(s -> mapLevel(s, Map.of(), Map.of()));
    }
    Map<UUID, Product> products = productsById(page.getContent());
    Map<UUID, Store> stores = storesById(page.getContent());
    return page.map(s -> mapLevel(s, products, stores));
  }

  private List<StockLevelResponse> mapLevels(List<ProductStoreStock> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<UUID, Product> products = productsById(rows);
    Map<UUID, Store> stores = storesById(rows);
    return rows.stream().map(s -> mapLevel(s, products, stores)).toList();
  }

  private Map<UUID, Product> productsById(List<ProductStoreStock> rows) {
    Map<UUID, Product> products = new HashMap<>();
    for (Product pr : productRepo.findAllById(rows.stream().map(ProductStoreStock::getProductId).distinct().toList())) {
      products.put(pr.getId(), pr);
    }
    return products;
  }

  private Map<UUID, Store> storesById(List<ProductStoreStock> rows) {
    Map<UUID, Store> stores = new HashMap<>();
    for (Store st : storeRepo.findAllById(rows.stream().map(ProductStoreStock::getStoreId).distinct().toList())) {
      stores.put(st.getId(), st);
    }
    return stores;
  }

  private StockLevelResponse mapLevel(
      ProductStoreStock s, Map<UUID, Product> products, Map<UUID, Store> stores) {
    Product pr = products.get(s.getProductId());
    Store st = stores.get(s.getStoreId());
    return new StockLevelResponse(
        s.getId(),
        s.getProductId(),
        pr != null ? pr.getName() : null,
        s.getStoreId(),
        st != null ? st.getName() : null,
        s.getQuantity(),
        s.getMinStock(),
        s.isLowStock(),
        s.getUpdatedAt(),
        pr != null ? pr.getSalePrice() : null,
        pr != null ? pr.getCategoryId() : null,
        pr != null ? pr.getImageUrl() : null);
  }

  private StockLevelResponse mapLevel(ProductStoreStock s) {
    return mapLevel(s, productsById(List.of(s)), storesById(List.of(s)));
  }

  private StockMovementResponse mapMov(StockMovement m) {
    return new StockMovementResponse(
        m.getId(),
        m.getProductId(),
        m.getStoreId(),
        m.getUserId(),
        m.getType(),
        m.getQuantity(),
        m.getQuantityBefore(),
        m.getQuantityAfter(),
        m.getReference(),
        m.getNote(),
        m.getCreatedAt());
  }
}
