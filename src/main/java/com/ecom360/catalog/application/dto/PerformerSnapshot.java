package com.ecom360.catalog.application.dto;

import java.util.UUID;

/** Intervenant retenu pour une ligne de vente, nom figé à l'encaissement. */
public record PerformerSnapshot(UUID businessUserId, String name) {}
