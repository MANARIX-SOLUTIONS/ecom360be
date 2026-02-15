package com.ecom360.store.domain.repository;

import com.ecom360.store.domain.model.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StoreRepository extends JpaRepository<Store, UUID> {
    List<Store> findByBusinessIdAndIsActive(UUID businessId, Boolean isActive);
    List<Store> findByBusinessId(UUID businessId);
    boolean existsByBusinessIdAndName(UUID businessId, String name);
}
