package com.ecom360.identity.domain.repository;

import com.ecom360.identity.domain.model.DemoRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DemoRequestRepository extends JpaRepository<DemoRequest, UUID> {

  Page<DemoRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

  Page<DemoRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

  boolean existsByEmailAndStatus(String email, String status);
}
