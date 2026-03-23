package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.Plan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
  Optional<Plan> findBySlug(String slug);

  List<Plan> findByIsActiveTrueOrderByPriceMonthlyAsc();
}
