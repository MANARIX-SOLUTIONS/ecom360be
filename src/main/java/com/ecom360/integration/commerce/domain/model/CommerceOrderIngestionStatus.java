package com.ecom360.integration.commerce.domain.model;

/**
 * Statut d’une entrée du journal d’ingestion. {@link #PROCESSED} sera utilisé lorsque la vente
 * interne sera créée (étape suivante).
 */
public enum CommerceOrderIngestionStatus {
  RECEIVED,
  DUPLICATE_SKIPPED,
  FAILED_VALIDATION,
  PROCESSED
}
