package com.ecom360.supplier.domain.repository;
import com.ecom360.supplier.domain.model.Supplier;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.stereotype.Repository;
import java.util.Optional; import java.util.UUID;
@Repository public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    Page<Supplier> findByBusinessIdAndIsActive(UUID bId, Boolean active, Pageable p);
    Optional<Supplier> findByBusinessIdAndId(UUID bId, UUID id);
    long countByBusinessId(UUID bId);
}
