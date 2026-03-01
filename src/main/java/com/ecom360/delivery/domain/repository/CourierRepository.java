package com.ecom360.delivery.domain.repository;

import com.ecom360.delivery.domain.model.Courier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourierRepository extends JpaRepository<Courier, UUID> {

  List<Courier> findByBusinessIdOrderByNameAsc(UUID businessId);

  List<Courier> findByBusinessIdAndIsActiveTrueOrderByNameAsc(UUID businessId);

  Optional<Courier> findByBusinessIdAndId(UUID businessId, UUID id);

  boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
}
