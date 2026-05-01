package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.Invoice;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
  Page<Invoice> findByBusinessIdOrderByCreatedAtDesc(UUID businessId, Pageable pageable);

  /**
   * Unpaid invoices whose due date is exactly {@code dueDate}. Excludes paid and void. Used when
   * billing reminders are enabled.
   */
  @Query(
      """
      SELECT i FROM Invoice i
      WHERE i.status NOT IN ('paid', 'void')
      AND i.dueDate = :dueDate
      """)
  List<Invoice> findUnpaidWithDueDate(@Param("dueDate") LocalDate dueDate);
}
