package com.ecom360.supplier.domain.repository;

import com.ecom360.supplier.domain.model.PurchaseOrderLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
  List<PurchaseOrderLine> findByPurchaseOrderId(UUID poId);

  void deleteByPurchaseOrderId(UUID poId);
}
