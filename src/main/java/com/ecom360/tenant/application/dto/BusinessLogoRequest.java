package com.ecom360.tenant.application.dto;

import jakarta.validation.constraints.Size;

/** Mettre à jour ou effacer (chaîne vide) le logo entreprise — plan Business requis pour une URL non vide. */
public record BusinessLogoRequest(@Size(max = 2048) String logoUrl) {}
