package com.ecom360.sales.domain.repository;
import com.ecom360.sales.domain.model.Sale;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param; import org.springframework.stereotype.Repository;
import java.time.Instant; import java.util.List; import java.util.Optional; import java.util.UUID;
@Repository public interface SaleRepository extends JpaRepository<Sale, UUID> {
    Page<Sale> findByBusinessIdAndStoreIdOrderByCreatedAtDesc(UUID bId, UUID sId, Pageable p);
    Page<Sale> findByBusinessIdOrderByCreatedAtDesc(UUID bId, Pageable p);
    Optional<Sale> findByBusinessIdAndId(UUID bId, UUID id);
    boolean existsByReceiptNumber(String r);
    long countByBusinessIdAndCreatedAtBetween(UUID bId, Instant s, Instant e);

    @Query("SELECT s.businessId, COALESCE(SUM(s.total), 0) FROM Sale s WHERE s.status = 'completed' AND s.createdAt BETWEEN :start AND :end GROUP BY s.businessId")
    List<Object[]> sumTotalByBusinessIdBetween(@Param("start") Instant start, @Param("end") Instant end);
}
