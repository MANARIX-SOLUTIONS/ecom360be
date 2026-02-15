package com.ecom360.inventory.domain.repository;
import com.ecom360.inventory.domain.model.StockMovement;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    Page<StockMovement> findByProductIdAndStoreIdOrderByCreatedAtDesc(UUID productId, UUID storeId, Pageable pageable);
    Page<StockMovement> findByStoreIdOrderByCreatedAtDesc(UUID storeId, Pageable pageable);
}
