package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.BusinessUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessUserRepository extends JpaRepository<BusinessUser, UUID> {
  Optional<BusinessUser> findByBusinessIdAndUserId(UUID businessId, UUID userId);

  List<BusinessUser> findByUserId(UUID userId);

  List<BusinessUser> findByBusinessId(UUID businessId);

  List<BusinessUser> findByBusinessIdAndIsActive(UUID businessId, Boolean isActive);

  boolean existsByBusinessIdAndUserId(UUID businessId, UUID userId);
}
