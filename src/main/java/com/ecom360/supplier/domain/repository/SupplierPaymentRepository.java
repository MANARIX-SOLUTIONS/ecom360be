package com.ecom360.supplier.domain.repository;

import com.ecom360.supplier.domain.model.SupplierPayment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, UUID> {
  Page<SupplierPayment> findBySupplierIdOrderByCreatedAtDesc(UUID sId, Pageable p);
}
