package com.ecom360.delivery.domain.repository;

import com.ecom360.delivery.domain.model.Delivery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

  List<Delivery> findByBusinessIdAndCourierIdOrderByDeliveredAtDesc(
      UUID businessId, UUID courierId, org.springframework.data.domain.Pageable pageable);

  List<Delivery> findByBusinessIdOrderByCreatedAtDesc(
      UUID businessId, org.springframework.data.domain.Pageable pageable);

  Optional<Delivery> findByBusinessIdAndId(UUID businessId, UUID id);

  @Query(
      "SELECT COALESCE(SUM(d.parcelsCount), 0) FROM Delivery d WHERE d.courierId = :courierId"
          + " AND d.businessId = :businessId AND d.status = 'delivered'")
  long sumParcelsDeliveredByCourierAndBusiness(
      @Param("courierId") UUID courierId, @Param("businessId") UUID businessId);

  @Query(
      "SELECT COUNT(d) FROM Delivery d WHERE d.courierId = :courierId AND d.businessId = :businessId"
          + " AND d.status = 'delivered'")
  long countDeliveredByCourierAndBusiness(
      @Param("courierId") UUID courierId, @Param("businessId") UUID businessId);

  @Query(
      "SELECT COUNT(d) FROM Delivery d WHERE d.courierId = :courierId AND d.businessId = :businessId"
          + " AND d.status = 'failed'")
  long countFailedByCourierAndBusiness(
      @Param("courierId") UUID courierId, @Param("businessId") UUID businessId);

  @Query(
      "SELECT COUNT(d) FROM Delivery d WHERE d.courierId = :courierId AND d.businessId = :businessId"
          + " AND d.status IN ('delivered', 'failed')")
  long countCompletedByCourierAndBusiness(
      @Param("courierId") UUID courierId, @Param("businessId") UUID businessId);

  /** Stats agrégées par livreur (courier_id, total_parcels, delivered_count, failed_count). */
  @Query(
      "SELECT d.courierId, COALESCE(SUM(CASE WHEN d.status = 'delivered' THEN d.parcelsCount ELSE 0 END), 0),"
          + " COUNT(CASE WHEN d.status = 'delivered' THEN 1 END),"
          + " COUNT(CASE WHEN d.status = 'failed' THEN 1 END) FROM Delivery d"
          + " WHERE d.businessId = :businessId GROUP BY d.courierId")
  List<Object[]> findDeliveryStatsByBusinessId(@Param("businessId") UUID businessId);
}
