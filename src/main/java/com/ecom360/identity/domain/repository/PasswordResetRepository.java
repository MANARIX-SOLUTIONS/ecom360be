package com.ecom360.identity.domain.repository;

import com.ecom360.identity.domain.model.PasswordReset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {
  Optional<PasswordReset> findByTokenHashAndUsedFalse(String tokenHash);
}
