package com.ecom360.identity.domain.repository;

import com.ecom360.identity.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  Page<User> findByIsActive(Boolean isActive, Pageable pageable);

  Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Query(
      value =
          """
        SELECT DISTINCT u.* FROM users u
        LEFT JOIN business_user bu ON bu.user_id = u.id
        LEFT JOIN business_role br ON br.id = bu.role_id
        LEFT JOIN business b ON b.id = bu.business_id
        WHERE (:q IS NULL OR :q = '' OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(b.name) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:active IS NULL OR u.is_active = :active)
        AND (:role IS NULL OR :role = '' OR :role = 'all' OR LOWER(br.code) = LOWER(:role))
        ORDER BY u.created_at DESC
        """,
      countQuery =
          """
        SELECT COUNT(DISTINCT u.id) FROM users u
        LEFT JOIN business_user bu ON bu.user_id = u.id
        LEFT JOIN business_role br ON br.id = bu.role_id
        LEFT JOIN business b ON b.id = bu.business_id
        WHERE (:q IS NULL OR :q = '' OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(b.name) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:active IS NULL OR u.is_active = :active)
        AND (:role IS NULL OR :role = '' OR :role = 'all' OR LOWER(br.code) = LOWER(:role))
        """,
      nativeQuery = true)
  Page<User> searchByNameEmailOrBusiness(
      @Param("q") String q,
      @Param("active") Boolean active,
      @Param("role") String role,
      Pageable pageable);
}
