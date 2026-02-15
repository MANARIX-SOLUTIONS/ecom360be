package com.ecom360.client.domain.repository;
import com.ecom360.client.domain.model.Client;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.stereotype.Repository;
import java.util.Optional; import java.util.UUID;
@Repository public interface ClientRepository extends JpaRepository<Client, UUID> {
    Page<Client> findByBusinessIdAndIsActive(UUID bId, Boolean active, Pageable p);
    Optional<Client> findByBusinessIdAndId(UUID bId, UUID id);
    long countByBusinessId(UUID bId);
}
