package com.ecom360.sales.domain.repository;

import com.ecom360.sales.domain.model.SaleLine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SaleLineRepository extends JpaRepository<SaleLine, UUID> {
  List<SaleLine> findBySaleId(UUID saleId);

  /**
   * Aggregates units sold and revenue per product across completed sales of a
   * business over a date range, optionally scoped to a store. Uses the sale
   * line's denormalized {@code productName}, so no per-product lookup is needed.
   * Rows are {@code [productId, productName, totalQuantity, totalRevenue]},
   * ordered by revenue descending.
   */
  @Query(
      "SELECT sl.productId, sl.productName,"
          + " COALESCE(SUM(sl.quantity), 0), COALESCE(SUM(sl.lineTotal), 0)"
          + " FROM SaleLine sl, Sale s"
          + " WHERE s.id = sl.saleId AND s.businessId = :bId AND s.status = 'completed'"
          + " AND s.createdAt >= :start AND s.createdAt < :end"
          + " AND (:storeId IS NULL OR s.storeId = :storeId)"
          + " GROUP BY sl.productId, sl.productName"
          + " ORDER BY COALESCE(SUM(sl.lineTotal), 0) DESC, sl.productId ASC")
  List<Object[]> aggregateProductSalesBetween(
      @Param("bId") UUID businessId,
      @Param("storeId") UUID storeId,
      @Param("start") Instant start,
      @Param("end") Instant end);
}
