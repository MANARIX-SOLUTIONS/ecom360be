package com.ecom360.integration.commerce.domain.repository;

import com.ecom360.integration.commerce.domain.model.CommerceOrderIngestionLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommerceOrderIngestionLogRepository
    extends JpaRepository<CommerceOrderIngestionLog, UUID> {

  @Query(
      """
      SELECT l FROM CommerceOrderIngestionLog l
      WHERE l.connectionId = :connectionId
        AND l.externalOrderId = :externalOrderId
        AND (l.status = 'PROCESSED' OR l.saleId IS NOT NULL)
      """)
  List<CommerceOrderIngestionLog> findSuccessfulIngestions(
      @Param("connectionId") UUID connectionId, @Param("externalOrderId") String externalOrderId);
}
