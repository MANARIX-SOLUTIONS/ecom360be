package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.Invoice;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
  Page<Invoice> findByBusinessIdOrderByCreatedAtDesc(UUID businessId, Pageable pageable);
}
