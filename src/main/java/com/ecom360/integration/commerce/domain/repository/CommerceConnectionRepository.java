package com.ecom360.integration.commerce.domain.repository;

import com.ecom360.integration.commerce.domain.model.CommerceConnection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceConnectionRepository extends JpaRepository<CommerceConnection, UUID> {

  Optional<CommerceConnection> findByIncomingToken(String incomingToken);

  List<CommerceConnection> findByBusinessIdOrderByLabelAsc(UUID businessId);

  Optional<CommerceConnection> findByBusinessIdAndId(UUID businessId, UUID id);
}
