package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.BusinessUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessUserRepository extends JpaRepository<BusinessUser, UUID> {
  Optional<BusinessUser> findByBusinessIdAndUserId(UUID businessId, UUID userId);

  @Query("SELECT bu FROM BusinessUser bu JOIN FETCH bu.businessRole WHERE bu.userId = :userId")
  List<BusinessUser> findByUserId(@Param("userId") UUID userId);

  List<BusinessUser> findByBusinessId(UUID businessId);

  @Query(
      "SELECT bu FROM BusinessUser bu JOIN FETCH bu.businessRole WHERE bu.businessId = :businessId ORDER BY bu.createdAt ASC")
  List<BusinessUser> findByBusinessIdOrderByCreatedAtWithRole(@Param("businessId") UUID businessId);

  @Query(
      "SELECT bu FROM BusinessUser bu JOIN FETCH bu.businessRole WHERE bu.businessId = :businessId AND bu.isActive = :active")
  List<BusinessUser> findByBusinessIdAndIsActive(
      @Param("businessId") UUID businessId, @Param("active") Boolean active);

  @Query("SELECT bu FROM BusinessUser bu JOIN FETCH bu.businessRole WHERE bu.id = :id")
  Optional<BusinessUser> findByIdWithRole(@Param("id") UUID id);

  boolean existsByBusinessIdAndUserId(UUID businessId, UUID userId);
}
