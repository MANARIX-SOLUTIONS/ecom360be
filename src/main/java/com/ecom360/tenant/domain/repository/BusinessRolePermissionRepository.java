package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.BusinessRolePermission;
import com.ecom360.tenant.domain.model.BusinessRolePermissionId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessRolePermissionRepository
    extends JpaRepository<BusinessRolePermission, BusinessRolePermissionId> {

  @Query(
      "SELECT p.code FROM BusinessRolePermission brp JOIN brp.permission p WHERE brp.role.id = :roleId")
  List<String> findPermissionCodesByRoleId(@Param("roleId") UUID roleId);

  @Modifying
  @Query("DELETE FROM BusinessRolePermission brp WHERE brp.role.id = :roleId")
  void deleteByRoleId(@Param("roleId") UUID roleId);
}
