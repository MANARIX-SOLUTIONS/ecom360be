package com.ecom360.sales.application.dto;

import java.util.UUID;

/** Ligne de vente importée (prix unitaire issu du site, pas forcément le catalogue POS). */
public record ImportedSaleLine(
    UUID productId, String lineLabel, int quantity, int unitPriceMinorUnits) {}
