package com.ecom360.supplier.domain.repository;

import com.ecom360.supplier.domain.model.Supplier;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
  Page<Supplier> findByBusinessIdAndIsActive(UUID bId, Boolean active, Pageable p);

  Optional<Supplier> findByBusinessIdAndId(UUID bId, UUID id);

  long countByBusinessId(UUID bId);

  long countByBusinessIdAndIsActive(UUID bId, Boolean active);

  @Query("""
      SELECT s FROM Supplier s
      WHERE s.businessId = :bId AND s.isActive = TRUE
        AND (:q IS NULL OR :q = ''
          OR LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(s.phone, '')) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(s.email, '')) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(s.zone, '')) LIKE LOWER(CONCAT('%', :q, '%')))
      """)
  Page<Supplier> searchActive(
      @Param("bId") UUID businessId, @Param("q") String q, Pageable pageable);
}
