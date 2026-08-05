package com.ecom360.supplier.domain.repository;

import com.ecom360.supplier.domain.model.PurchaseOrderPayment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderPaymentRepository
    extends JpaRepository<PurchaseOrderPayment, UUID> {

  List<PurchaseOrderPayment> findByPurchaseOrderIdOrderByCreatedAtAsc(UUID purchaseOrderId);
}
