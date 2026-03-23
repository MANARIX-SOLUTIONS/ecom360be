package com.ecom360.identity.domain.repository;

import com.ecom360.identity.domain.model.Session;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
  Optional<Session> findByTokenHash(String tokenHash);

  List<Session> findByUserId(UUID userId);

  void deleteByExpiresAtBefore(Instant expiration);

  void deleteByUserId(UUID userId);
}
