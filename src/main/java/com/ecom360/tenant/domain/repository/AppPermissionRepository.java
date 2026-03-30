package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.AppPermission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppPermissionRepository extends JpaRepository<AppPermission, UUID> {
  Optional<AppPermission> findByCode(String code);

  List<AppPermission> findAllByOrderByCodeAsc();

  List<AppPermission> findAllByOrderBySortOrderAscCodeAsc();
}
