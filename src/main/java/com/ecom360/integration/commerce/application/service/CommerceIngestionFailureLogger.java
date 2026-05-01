package com.ecom360.integration.commerce.application.service;

import com.ecom360.integration.commerce.application.dto.CanonicalOrderPayload;
import com.ecom360.integration.commerce.domain.model.CommerceConnection;
import com.ecom360.integration.commerce.domain.model.CommerceOrderIngestionLog;
import com.ecom360.integration.commerce.domain.model.CommerceOrderIngestionStatus;
import com.ecom360.integration.commerce.domain.repository.CommerceOrderIngestionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Journalise les rejets de validation dans une transaction séparée pour que l’entrée survive au
 * rollback du flux principal.
 */
@Service
public class CommerceIngestionFailureLogger {

  private final CommerceOrderIngestionLogRepository ingestionLogRepository;

  public CommerceIngestionFailureLogger(
      CommerceOrderIngestionLogRepository ingestionLogRepository) {
    this.ingestionLogRepository = ingestionLogRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void logValidationFailure(
      CommerceConnection connection,
      CanonicalOrderPayload payloadOrNull,
      String payloadHash,
      String storedRaw,
      String errorMessage) {
    CommerceOrderIngestionLog log = new CommerceOrderIngestionLog();
    log.setConnectionId(connection.getId());
    log.setBusinessId(connection.getBusinessId());
    log.setSourceType(
        payloadOrNull != null ? payloadOrNull.sourceType() : connection.getSourceType());
    log.setExternalOrderId(
        payloadOrNull != null && payloadOrNull.externalOrderId() != null
            ? payloadOrNull.externalOrderId()
            : "unknown");
    log.setStatus(CommerceOrderIngestionStatus.FAILED_VALIDATION.name());
    log.setPayloadHash(payloadHash);
    log.setRawPayload(storedRaw);
    log.setErrorMessage(truncate(errorMessage, 4000));
    ingestionLogRepository.save(log);
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }
}
