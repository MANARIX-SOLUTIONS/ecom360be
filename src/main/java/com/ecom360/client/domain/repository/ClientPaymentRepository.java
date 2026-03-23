package com.ecom360.client.domain.repository;

import com.ecom360.client.domain.model.ClientPayment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientPaymentRepository extends JpaRepository<ClientPayment, UUID> {
  Page<ClientPayment> findByClientIdOrderByCreatedAtDesc(UUID cId, Pageable p);
}
