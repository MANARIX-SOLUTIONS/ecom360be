package com.ecom360.admin.application.dto;

import jakarta.validation.constraints.Size;

/**
 * Renouvellement admin — les deux champs sont optionnels : par défaut on reprend le plan et le
 * cycle de l'abonnement courant (ou dernier).
 */
public record AdminRenewSubscriptionRequest(
    @Size(max = 50) String planSlug, @Size(max = 20) String billingCycle) {}
