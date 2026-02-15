package com.ecom360.sales.domain.repository;
import com.ecom360.sales.domain.model.SaleLine;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.stereotype.Repository;
import java.util.List; import java.util.UUID;
@Repository public interface SaleLineRepository extends JpaRepository<SaleLine, UUID> { List<SaleLine> findBySaleId(UUID saleId); }
