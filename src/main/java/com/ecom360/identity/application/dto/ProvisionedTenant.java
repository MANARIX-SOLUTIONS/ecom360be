package com.ecom360.identity.application.dto;

import java.util.UUID;

/** Résultat de la création tenant après validation d'une demande démo. */
public record ProvisionedTenant(UUID businessId, UUID userId) {}
