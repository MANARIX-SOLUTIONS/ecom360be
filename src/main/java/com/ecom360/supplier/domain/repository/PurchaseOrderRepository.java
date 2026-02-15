package com.ecom360.supplier.domain.repository;
import com.ecom360.supplier.domain.model.PurchaseOrder;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.stereotype.Repository;
import java.util.Optional; import java.util.UUID;
@Repository public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Page<PurchaseOrder> findByBusinessIdOrderByCreatedAtDesc(UUID bId, Pageable p);
    Page<PurchaseOrder> findByBusinessIdAndStatusOrderByCreatedAtDesc(UUID bId, String s, Pageable p);
    Page<PurchaseOrder> findByBusinessIdAndSupplierIdOrderByCreatedAtDesc(UUID bId, UUID sId, Pageable p);
    Optional<PurchaseOrder> findByBusinessIdAndId(UUID bId, UUID id);
    boolean existsByReference(String r);
    long countByBusinessId(UUID bId);
}
