package com.ecom360.inventory.domain.repository;

import com.ecom360.inventory.domain.model.ProductStoreStock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductStoreStockRepository extends JpaRepository<ProductStoreStock, UUID> {
  Optional<ProductStoreStock> findByProductIdAndStoreId(UUID productId, UUID storeId);

  boolean existsByProductIdAndStoreId(UUID productId, UUID storeId);

  /**
   * Full store stock (dashboard / low-stock scans). Prefer the pageable overload
   * for UI lists.
   */
  List<ProductStoreStock> findByStoreId(UUID storeId);

  Page<ProductStoreStock> findByStoreId(UUID storeId, Pageable pageable);

  List<ProductStoreStock> findByStoreIdAndProductIdIn(UUID storeId, Collection<UUID> productIds);

  @Query("""
      SELECT s FROM ProductStoreStock s, Product p
      WHERE s.storeId = :storeId AND p.id = s.productId
        AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(p.sku, '')) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :q, '%')))
      """)
  Page<ProductStoreStock> searchByStoreId(
      @Param("storeId") UUID storeId, @Param("q") String q, Pageable pageable);
}
