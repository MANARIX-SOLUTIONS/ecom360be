package com.ecom360.catalog.domain.repository;

import com.ecom360.catalog.domain.model.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
  Page<Product> findByBusinessIdAndIsActive(UUID businessId, Boolean isActive, Pageable pageable);

  long countByBusinessIdAndIsActive(UUID businessId, Boolean isActive);

  Page<Product> findByBusinessId(UUID businessId, Pageable pageable);

  Optional<Product> findByBusinessIdAndId(UUID businessId, UUID id);

  long countByBusinessId(UUID businessId);

  boolean existsByBusinessIdAndSku(UUID businessId, String sku);

  @Query(
      """
      SELECT p FROM Product p
      WHERE p.businessId = :bid AND p.storeId = :sid
        AND p.sku IS NOT NULL AND LOWER(TRIM(p.sku)) = LOWER(TRIM(:sku))
      """)
  Optional<Product> findByBusinessIdAndStoreIdAndSkuNormalized(
      @Param("bid") UUID businessId, @Param("sid") UUID storeId, @Param("sku") String sku);

  Page<Product> findByBusinessIdAndCategoryIdAndIsActive(
      UUID businessId, UUID categoryId, Boolean isActive, Pageable pageable);

  @Query(
      """
      SELECT p FROM Product p
      WHERE p.businessId = :bid
        AND p.isActive = :active
        AND (:cat IS NULL OR p.categoryId = :cat)
        AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :s, '%'))
          OR LOWER(COALESCE(p.sku, '')) LIKE LOWER(CONCAT('%', :s, '%'))
          OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :s, '%')))
      """)
  Page<Product> searchByBusinessId(
      @Param("bid") UUID bid,
      @Param("s") String s,
      @Param("cat") UUID categoryId,
      @Param("active") Boolean active,
      Pageable pageable);

  long countByBusinessIdAndCategoryId(UUID businessId, UUID categoryId);

  long countByBusinessIdAndCategoryIdAndIsActive(
      UUID businessId, UUID categoryId, Boolean isActive);

  Page<Product> findByBusinessIdAndStoreIdAndIsActive(
      UUID businessId, UUID storeId, Boolean isActive, Pageable pageable);

  Page<Product> findByBusinessIdAndStoreIdAndCategoryIdAndIsActive(
      UUID businessId, UUID storeId, UUID categoryId, Boolean isActive, Pageable pageable);

  @Query(
      """
      SELECT p FROM Product p
      WHERE p.businessId = :bid AND p.storeId = :sid
        AND p.isActive = :active
        AND (:cat IS NULL OR p.categoryId = :cat)
        AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :s, '%'))
          OR LOWER(COALESCE(p.sku, '')) LIKE LOWER(CONCAT('%', :s, '%'))
          OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :s, '%')))
      """)
  Page<Product> searchByBusinessIdAndStoreId(
      @Param("bid") UUID bid,
      @Param("sid") UUID storeId,
      @Param("s") String s,
      @Param("cat") UUID categoryId,
      @Param("active") Boolean active,
      Pageable pageable);
}
