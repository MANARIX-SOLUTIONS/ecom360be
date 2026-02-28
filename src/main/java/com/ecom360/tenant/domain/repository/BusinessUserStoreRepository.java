package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.BusinessUserStore;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessUserStoreRepository extends JpaRepository<BusinessUserStore, UUID> {
  List<BusinessUserStore> findByBusinessUserId(UUID businessUserId);

  void deleteByBusinessUserIdAndStoreId(UUID businessUserId, UUID storeId);

  void deleteByBusinessUserId(UUID businessUserId);

  boolean existsByBusinessUserIdAndStoreId(UUID businessUserId, UUID storeId);
}
