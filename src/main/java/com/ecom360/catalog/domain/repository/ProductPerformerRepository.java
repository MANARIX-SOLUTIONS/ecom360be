package com.ecom360.catalog.domain.repository;

import com.ecom360.catalog.domain.model.ProductPerformer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductPerformerRepository extends JpaRepository<ProductPerformer, UUID> {
  List<ProductPerformer> findByProductId(UUID productId);

  boolean existsByProductIdAndBusinessUserId(UUID productId, UUID businessUserId);

  void deleteByProductId(UUID productId);
}
