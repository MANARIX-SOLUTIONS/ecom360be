package com.ecom360.inventory.domain.repository;
import com.ecom360.inventory.domain.model.ProductStoreStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List; import java.util.Optional; import java.util.UUID;
@Repository public interface ProductStoreStockRepository extends JpaRepository<ProductStoreStock, UUID> {
    Optional<ProductStoreStock> findByProductIdAndStoreId(UUID productId, UUID storeId);
    boolean existsByProductIdAndStoreId(UUID productId, UUID storeId);
    List<ProductStoreStock> findByStoreId(UUID storeId);
}
