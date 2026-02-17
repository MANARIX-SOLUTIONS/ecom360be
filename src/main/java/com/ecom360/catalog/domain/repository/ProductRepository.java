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

  Page<Product> findByBusinessId(UUID businessId, Pageable pageable);

  Optional<Product> findByBusinessIdAndId(UUID businessId, UUID id);

  long countByBusinessId(UUID businessId);

  boolean existsByBusinessIdAndSku(UUID businessId, String sku);

  @Query(
      "SELECT p FROM Product p WHERE p.businessId = :bid AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :s, '%')) OR LOWER(COALESCE(p.sku, '')) LIKE LOWER(CONCAT('%', :s, '%')) OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :s, '%')))")
  Page<Product> searchByBusinessId(@Param("bid") UUID bid, @Param("s") String s, Pageable pageable);

  long countByBusinessIdAndCategoryId(UUID businessId, UUID categoryId);
}
