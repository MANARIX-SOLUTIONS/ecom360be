package com.ecom360.sales.domain.repository;

import com.ecom360.sales.domain.model.SalePayment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SalePaymentRepository extends JpaRepository<SalePayment, UUID> {

  List<SalePayment> findBySaleIdOrderByCreatedAtAsc(UUID saleId);

  /**
   * Montant réellement encaissé sur la période {@code [start, end)}, optionnellement
   * limité à une boutique. Contrairement au CA facturé (SUM(sale.total)), ce total
   * suit la trésorerie.
   */
  @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM SalePayment sp"
      + " WHERE sp.businessId = :bId"
      + " AND sp.createdAt >= :start AND sp.createdAt < :end"
      + " AND (:storeId IS NULL OR sp.storeId = :storeId)")
  long sumCollectedBetween(
      @Param("bId") UUID businessId,
      @Param("storeId") UUID storeId,
      @Param("start") Instant start,
      @Param("end") Instant end);
}
