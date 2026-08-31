package com.ecom360.supplier.domain.repository;

import com.ecom360.supplier.domain.model.PurchaseOrder;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Page<PurchaseOrder> findByBusinessIdOrderByCreatedAtDesc(UUID bId, Pageable p);

    Page<PurchaseOrder> findByBusinessIdAndStatusOrderByCreatedAtDesc(UUID bId, String s, Pageable p);

    Page<PurchaseOrder> findByBusinessIdAndSupplierIdOrderByCreatedAtDesc(
            UUID bId, UUID sId, Pageable p);

    Page<PurchaseOrder> findByBusinessIdAndStatusAndSupplierIdOrderByCreatedAtDesc(
            UUID bId, String status, UUID supplierId, Pageable p);

    Optional<PurchaseOrder> findByBusinessIdAndId(UUID bId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PurchaseOrder p WHERE p.businessId = :bId AND p.id = :id")
    Optional<PurchaseOrder> findByBusinessIdAndIdForUpdate(
            @Param("bId") UUID bId, @Param("id") UUID id);

    boolean existsByReference(String r);

    long countByBusinessId(UUID bId);

    /**
     * Bons réceptionnés d'un fournisseur encore partiellement ou totalement
     * impayés, du plus ancien au plus récent (imputation FIFO des paiements).
     */
    @Query("SELECT p FROM PurchaseOrder p WHERE p.businessId = :bId AND p.supplierId = :supplierId"
            + " AND p.status = 'received' AND p.amountPaid < p.totalAmount"
            + " ORDER BY p.createdAt ASC, p.id ASC")
    List<PurchaseOrder> findOutstandingBySupplier(
            @Param("bId") UUID businessId, @Param("supplierId") UUID supplierId);
}
