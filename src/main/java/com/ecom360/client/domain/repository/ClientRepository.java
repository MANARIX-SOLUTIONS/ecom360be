package com.ecom360.client.domain.repository;

import com.ecom360.client.domain.model.Client;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
  Page<Client> findByBusinessIdAndIsActive(UUID bId, Boolean active, Pageable p);

  Optional<Client> findByBusinessIdAndId(UUID bId, UUID id);

  long countByBusinessId(UUID bId);

  @Query("SELECT COUNT(c) FROM Client c WHERE c.businessId = :bId AND c.isActive = TRUE AND"
      + " c.creditBalance > 0")
  long countDebtorsWithPositiveBalance(@Param("bId") UUID bId);

  @Query("SELECT COALESCE(SUM(c.creditBalance), 0) FROM Client c WHERE c.businessId = :bId AND"
      + " c.isActive = TRUE AND c.creditBalance > 0")
  long sumPositiveCreditBalance(@Param("bId") UUID bId);
}
