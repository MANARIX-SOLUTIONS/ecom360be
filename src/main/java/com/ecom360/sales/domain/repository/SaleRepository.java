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

        /**
         * Completed revenue for a business over a half-open Instant window
         * {@code [start, end)}, optionally scoped to one store.
         * Scalar return avoids Spring Data / Hibernate mis-wrapping multi-column
         * {@code Object[]} aggregates (which previously zeroed Vue globale KPIs).
         */
        @Query("SELECT COALESCE(SUM(s.total), 0) FROM Sale s"
                        + " WHERE s.businessId = :bId AND s.status = 'completed'"
                        + " AND s.createdAt >= :start AND s.createdAt < :end"
                        + " AND (:storeId IS NULL OR s.storeId = :storeId)")
        long sumCompletedTotalBetween(
                        @Param("bId") UUID businessId,
                        @Param("storeId") UUID storeId,
                        @Param("start") Instant start,
                        @Param("end") Instant end);

        /**
         * Completed sales count for a business over {@code [start, end)}, optionally
         * scoped to one store.
         */
        @Query("SELECT COUNT(s) FROM Sale s"
                        + " WHERE s.businessId = :bId AND s.status = 'completed'"
                        + " AND s.createdAt >= :start AND s.createdAt < :end"
                        + " AND (:storeId IS NULL OR s.storeId = :storeId)")
        long countCompletedBetween(
                        @Param("bId") UUID businessId,
                        @Param("storeId") UUID storeId,
                        @Param("start") Instant start,
                        @Param("end") Instant end);

        @Query("SELECT s.businessId, COALESCE(SUM(s.total), 0) FROM Sale s WHERE s.status = 'completed' AND s.createdAt BETWEEN :start AND :end GROUP BY s.businessId")
        List<Object[]> sumTotalByBusinessIdBetween(
                        @Param("start") Instant start, @Param("end") Instant end);

        /**
         * Revenue and count per store for a business in date range (completed sales
         * only).
         */
        @Query("SELECT s.storeId, COALESCE(SUM(s.total), 0), COUNT(s) FROM Sale s"
                        + " WHERE s.businessId = :bId AND s.status = 'completed'"
                        + " AND s.createdAt >= :start AND s.createdAt < :end"
                        + " GROUP BY s.storeId")
        List<Object[]> sumRevenueAndCountByStoreIdBetween(
                        @Param("bId") UUID businessId, @Param("start") Instant start, @Param("end") Instant end);

        /**
         * Daily completed revenue. Returns rows
         * {@code [java.sql.Date|LocalDate day, Number amount]}.
         * Day boundary follows the database session timezone (aligned with Instant
         * window from
         * {@code ZoneId.systemDefault()} when the JVM and DB share the same zone).
         */
        @Query("SELECT FUNCTION('DATE', s.createdAt), COALESCE(SUM(s.total), 0) FROM Sale s"
                        + " WHERE s.businessId = :bId AND s.status = 'completed'"
                        + " AND s.createdAt >= :start AND s.createdAt < :end"
                        + " AND (:storeId IS NULL OR s.storeId = :storeId)"
                        + " GROUP BY FUNCTION('DATE', s.createdAt)"
                        + " ORDER BY FUNCTION('DATE', s.createdAt)")
        List<Object[]> sumRevenueGroupedByDayBetween(
                        @Param("bId") UUID businessId,
                        @Param("storeId") UUID storeId,
                        @Param("start") Instant start,
                        @Param("end") Instant end);

        /**
         * Completed revenue by payment method. Returns
         * {@code [String method, Number amount]}.
         */
        @Query("SELECT s.paymentMethod, COALESCE(SUM(s.total), 0) FROM Sale s"
                        + " WHERE s.businessId = :bId AND s.status = 'completed'"
                        + " AND s.createdAt >= :start AND s.createdAt < :end"
                        + " AND (:storeId IS NULL OR s.storeId = :storeId)"
                        + " GROUP BY s.paymentMethod")
        List<Object[]> sumRevenueGroupedByPaymentMethodBetween(
                        @Param("bId") UUID businessId,
                        @Param("storeId") UUID storeId,
                        @Param("start") Instant start,
                        @Param("end") Instant end);

        @Query("SELECT s FROM Sale s WHERE s.businessId = :bId "
                        + "AND (:storeId IS NULL OR s.storeId = :storeId) "
                        + "AND (:status IS NULL OR s.status = :status) "
                        + "AND (:paymentStatus IS NULL OR s.paymentStatus = :paymentStatus) "
                        + "AND (:clientId IS NULL OR s.clientId = :clientId) "
                        + "AND (CAST(:from AS timestamp) IS NULL OR s.createdAt >= :from) "
                        + "AND (CAST(:to AS timestamp) IS NULL OR s.createdAt < :to) "
                        + "ORDER BY s.createdAt DESC")
        Page<Sale> findFiltered(
                        @Param("bId") UUID businessId,
                        @Param("storeId") UUID storeId,
                        @Param("status") String status,
                        @Param("paymentStatus") String paymentStatus,
                        @Param("clientId") UUID clientId,
                        @Param("from") Instant from,
                        @Param("to") Instant to,
                        Pageable pageable);

        /**
         * Ventes validées d'un client encore partiellement ou totalement impayées,
         * de la plus ancienne à la plus récente (imputation FIFO des remboursements).
         */
        @Query("SELECT s FROM Sale s WHERE s.businessId = :bId AND s.clientId = :clientId"
                        + " AND s.status = 'completed' AND s.amountPaid < s.total"
                        + " ORDER BY s.createdAt ASC, s.id ASC")
        List<Sale> findOutstandingByClient(
                        @Param("bId") UUID businessId, @Param("clientId") UUID clientId);

        /** Reste à encaisser sur l'ensemble des ventes validées, optionnellement par boutique. */
        @Query("SELECT COALESCE(SUM(s.total - s.amountPaid), 0) FROM Sale s"
                        + " WHERE s.businessId = :bId AND s.status = 'completed'"
                        + " AND s.amountPaid < s.total"
                        + " AND (:storeId IS NULL OR s.storeId = :storeId)")
        long sumOutstanding(@Param("bId") UUID businessId, @Param("storeId") UUID storeId);
}
