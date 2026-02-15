package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.Business;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessRepository extends JpaRepository<Business, UUID> {
    Optional<Business> findByEmail(String email);
    boolean existsByEmail(String email);
    Page<Business> findByStatus(String status, Pageable pageable);
    Page<Business> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = """
        SELECT DISTINCT b.* FROM business b
        LEFT JOIN business_user bu ON bu.business_id = b.id AND bu.role = 'proprietaire'
        LEFT JOIN users u ON u.id = bu.user_id
        LEFT JOIN subscription sub ON sub.business_id = b.id AND sub.status IN ('active', 'trialing')
        LEFT JOIN plan p ON p.id = sub.plan_id
        WHERE (:q IS NULL OR :q = '' OR LOWER(b.name) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(b.email) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:status IS NULL OR :status = '' OR b.status = :status)
        AND (:plan IS NULL OR :plan = '' OR :plan = 'all' OR p.name = :plan)
        ORDER BY b.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(DISTINCT b.id) FROM business b
        LEFT JOIN business_user bu ON bu.business_id = b.id AND bu.role = 'proprietaire'
        LEFT JOIN users u ON u.id = bu.user_id
        LEFT JOIN subscription sub ON sub.business_id = b.id AND sub.status IN ('active', 'trialing')
        LEFT JOIN plan p ON p.id = sub.plan_id
        WHERE (:q IS NULL OR :q = '' OR LOWER(b.name) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(b.email) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:status IS NULL OR :status = '' OR b.status = :status)
        AND (:plan IS NULL OR :plan = '' OR :plan = 'all' OR p.name = :plan)
        """,
        nativeQuery = true)
    Page<Business> searchByNameOrOwner(@Param("q") String q, @Param("status") String status, @Param("plan") String plan, Pageable pageable);
}
