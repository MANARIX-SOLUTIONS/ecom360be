package com.ecom360.inventory.application.service;

import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.inventory.domain.model.ProductStoreStock;
import com.ecom360.inventory.domain.model.StockMovement;
import com.ecom360.inventory.domain.repository.ProductStoreStockRepository;
import com.ecom360.inventory.domain.repository.StockMovementRepository;
import com.ecom360.store.domain.model.Store;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedCatalogStockService {
  private final BusinessRepository businessRepository;
  private final StoreRepository storeRepository;
  private final ProductRepository productRepository;
  private final ProductStoreStockRepository stockRepository;
  private final StockMovementRepository movementRepository;

  public SharedCatalogStockService(
      BusinessRepository businessRepository,
      StoreRepository storeRepository,
      ProductRepository productRepository,
      ProductStoreStockRepository stockRepository,
      StockMovementRepository movementRepository) {
    this.businessRepository = businessRepository;
    this.storeRepository = storeRepository;
    this.productRepository = productRepository;
    this.stockRepository = stockRepository;
    this.movementRepository = movementRepository;
  }

  public boolean isSharedCatalog(UUID businessId) {
    return businessRepository.findById(businessId).map(Business::hasSharedCatalog).orElse(false);
  }

  @Transactional
  public void provisionForNewProduct(
      UUID businessId,
      UUID productId,
      UUID originStoreId,
      int initialQty,
      int minStock,
      UUID userId) {
    int safeQty = Math.max(initialQty, 0);
    int safeMin = Math.max(minStock, 0);
    for (Store store : storeRepository.findByBusinessId(businessId)) {
      int qty = store.getId().equals(originStoreId) ? safeQty : 0;
      ensureStock(productId, store.getId(), qty, safeMin);
      if (qty > 0 && userId != null) {
        movementRepository.save(
            StockMovement.record(
                productId,
                store.getId(),
                userId,
                "in",
                qty,
                0,
                qty,
                null,
                "Initial stock"));
      }
    }
  }

  @Transactional
  public void backfillMissingForBusiness(UUID businessId) {
    List<Product> products = productRepository.findAllByBusinessIdAndIsActive(businessId, true);
    List<Store> stores = storeRepository.findByBusinessId(businessId);
    for (Product product : products) {
      for (Store store : stores) {
        ensureStock(product.getId(), store.getId(), 0, 0);
      }
    }
  }

  @Transactional
  public void backfillForNewStore(UUID businessId, UUID storeId) {
    for (Product product : productRepository.findAllByBusinessIdAndIsActive(businessId, true)) {
      ensureStock(product.getId(), storeId, 0, 0);
    }
  }

  private void ensureStock(UUID productId, UUID storeId, int quantity, int minStock) {
    if (stockRepository.existsByProductIdAndStoreId(productId, storeId)) {
      return;
    }
    ProductStoreStock row = new ProductStoreStock();
    row.setProductId(productId);
    row.setStoreId(storeId);
    row.setQuantity(quantity);
    row.setMinStock(minStock);
    stockRepository.save(row);
  }
}
