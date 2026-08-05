package com.ecom360.tenant.payment.domain.repository;

import com.ecom360.tenant.payment.domain.model.SubscriptionPaymentIntent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPaymentIntentRepository
    extends JpaRepository<SubscriptionPaymentIntent, UUID> {

  Optional<SubscriptionPaymentIntent> findByExternalToken(String externalToken);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM SubscriptionPaymentIntent i WHERE i.id = :id")
  Optional<SubscriptionPaymentIntent> findByIdForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM SubscriptionPaymentIntent i WHERE i.externalToken = :token")
  Optional<SubscriptionPaymentIntent> findByExternalTokenForUpdate(@Param("token") String token);

  Page<SubscriptionPaymentIntent> findByBusinessIdOrderByCreatedAtDesc(
      UUID businessId, Pageable pageable);

  @Query("""
      SELECT i FROM SubscriptionPaymentIntent i
      WHERE i.status = :status AND i.createdAt < :before
      """)
  List<SubscriptionPaymentIntent> findByStatusAndCreatedAtBefore(
      @Param("status") String status, @Param("before") Instant before);

  @Query("""
      SELECT i FROM SubscriptionPaymentIntent i
      WHERE (:businessId IS NULL OR i.businessId = :businessId)
        AND (:status IS NULL OR i.status = :status)
        AND (:from IS NULL OR i.createdAt >= :from)
        AND (:to IS NULL OR i.createdAt <= :to)
      ORDER BY i.createdAt DESC
      """)
  Page<SubscriptionPaymentIntent> search(
      @Param("businessId") UUID businessId,
      @Param("status") String status,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
