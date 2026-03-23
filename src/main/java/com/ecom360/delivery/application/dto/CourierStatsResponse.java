package com.ecom360.delivery.application.dto;

import java.util.UUID;

/** Indicateurs de performance d'un livreur. */
public record CourierStatsResponse(
    UUID courierId,
    long totalParcelsDelivered,
    long totalDeliveries,
    long failedDeliveries,
    double successRatePercent) {}
