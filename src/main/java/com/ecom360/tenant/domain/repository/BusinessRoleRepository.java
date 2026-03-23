package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.BusinessRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessRoleRepository extends JpaRepository<BusinessRole, UUID> {
  long countByBusinessId(UUID businessId);

  List<BusinessRole> findByBusinessIdOrderByCodeAsc(UUID businessId);

  Optional<BusinessRole> findByBusinessIdAndCode(UUID businessId, String code);

  boolean existsByBusinessIdAndCode(UUID businessId, String code);
}
