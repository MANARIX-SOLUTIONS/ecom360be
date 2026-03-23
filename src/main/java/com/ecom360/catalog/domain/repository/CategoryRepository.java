package com.ecom360.catalog.domain.repository;

import com.ecom360.catalog.domain.model.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
  List<Category> findByBusinessIdOrderBySortOrderAsc(UUID businessId);

  boolean existsByBusinessIdAndName(UUID businessId, String name);
}
