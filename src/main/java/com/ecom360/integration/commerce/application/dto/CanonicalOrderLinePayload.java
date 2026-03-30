package com.ecom360.integration.commerce.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CanonicalOrderLinePayload(
    /** Si renseigné, résolution directe du produit catalogue (magasin doit correspondre). */
    UUID productId,
    @Size(max = 120) String sku,
    @NotBlank @Size(max = 500) String label,
    @NotNull @Min(1) Integer quantity,
    @NotNull @Min(0) Integer unitPriceMinorUnits) {}
