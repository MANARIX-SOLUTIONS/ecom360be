package com.ecom360.sales.domain.repository;

import com.ecom360.sales.domain.model.Sale;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SaleRepository extends JpaRepository<Sale, UUID> {
  Page<Sale> findByBusinessIdAndStoreIdOrderByCreatedAtDesc(UUID bId, UUID sId, Pageable p);

  Page<Sale> findByBusinessIdOrderByCreatedAtDesc(UUID bId, Pageable p);

  Optional<Sale> findByBusinessIdAndId(UUID bId, UUID id);

  boolean existsByReceiptNumber(String r);

  long countByBusinessIdAndCreatedAtBetween(UUID bId, Instant s, Instant e);

  long countByBusinessIdAndStoreIdAndCreatedAtBetween(
      UUID bId, UUID storeId, Instant start, Instant end);

  @Query(
      "SELECT s.businessId, COALESCE(SUM(s.total), 0) FROM Sale s WHERE s.status = 'completed' AND s.createdAt BETWEEN :start AND :end GROUP BY s.businessId")
  List<Object[]> sumTotalByBusinessIdBetween(
      @Param("start") Instant start, @Param("end") Instant end);

  /** Revenue and count per store for a business in date range (completed sales only). */
  @Query(
      "SELECT s.storeId, COALESCE(SUM(s.total), 0), COUNT(s) FROM Sale s"
          + " WHERE s.businessId = :bId AND s.status = 'completed'"
          + " AND s.createdAt >= :start AND s.createdAt < :end"
          + " GROUP BY s.storeId")
  List<Object[]> sumRevenueAndCountByStoreIdBetween(
      @Param("bId") UUID businessId, @Param("start") Instant start, @Param("end") Instant end);

  @Query(
      "SELECT s FROM Sale s WHERE s.businessId = :bId "
          + "AND (:storeId IS NULL OR s.storeId = :storeId) "
          + "AND (:status IS NULL OR s.status = :status) "
          + "AND (CAST(:from AS timestamp) IS NULL OR s.createdAt >= :from) "
          + "AND (CAST(:to AS timestamp) IS NULL OR s.createdAt < :to) "
          + "ORDER BY s.createdAt DESC")
  Page<Sale> findFiltered(
      @Param("bId") UUID businessId,
      @Param("storeId") UUID storeId,
      @Param("status") String status,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
