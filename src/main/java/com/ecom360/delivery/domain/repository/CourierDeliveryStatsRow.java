package com.ecom360.delivery.domain.repository;

import java.util.UUID;

/** Projection for aggregated courier performance. */
public interface CourierDeliveryStatsRow {

  UUID getCourierId();

  Long getTotalParcels();

  Long getDeliveredCount();

  Long getFailedCount();
}
