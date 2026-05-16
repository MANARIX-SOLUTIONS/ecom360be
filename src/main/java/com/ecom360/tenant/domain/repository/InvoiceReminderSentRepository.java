package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.InvoiceReminderSent;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceReminderSentRepository extends JpaRepository<InvoiceReminderSent, UUID> {

  boolean existsByInvoiceIdAndReminderKindAndDueDate(
      UUID invoiceId, String reminderKind, LocalDate dueDate);
}
