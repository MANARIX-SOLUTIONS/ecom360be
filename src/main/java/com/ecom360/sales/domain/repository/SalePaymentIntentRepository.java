package com.ecom360.sales.domain.repository;

import com.ecom360.sales.domain.model.SalePaymentIntent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SalePaymentIntentRepository extends JpaRepository<SalePaymentIntent, UUID> {

  /** Scalar lookup: keeps the intent out of the persistence context before locking. */
  @Query("SELECT i.businessId FROM SalePaymentIntent i WHERE i.id = :id")
  Optional<UUID> findBusinessIdById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM SalePaymentIntent i WHERE i.id = :id")
  Optional<SalePaymentIntent> findByIdForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM SalePaymentIntent i WHERE i.externalToken = :token")
  Optional<SalePaymentIntent> findByExternalTokenForUpdate(@Param("token") String token);

  @Query("SELECT i.id FROM SalePaymentIntent i WHERE i.status = :status AND i.expiresAt < :now")
  List<UUID> findIdsByStatusAndExpiresAtBefore(
      @Param("status") String status, @Param("now") Instant now);
}
