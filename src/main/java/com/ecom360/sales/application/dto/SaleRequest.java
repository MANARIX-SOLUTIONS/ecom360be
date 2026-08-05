package com.ecom360.sales.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SaleRequest(
    @NotNull UUID storeId,
    @NotNull UUID clientId,
    @NotBlank @Pattern(regexp = "cash|wave|orange_money|credit") String paymentMethod,
    @Min(0) Integer discountAmount,
    @Min(0) Integer amountReceived,
    /**
     * Montant réellement encaissé à la validation. {@code null} conserve le
     * comportement historique : intégralité du total, sauf en mode crédit où rien
     * n'est encaissé.
     */
    @Min(0) Integer amountPaid,
    /** Échéance du solde quand la vente laisse un reste à payer. */
    LocalDate dueDate,
    String note,
    @NotEmpty @Valid List<SaleLineRequest> lines) {
  public SaleRequest {
    if (discountAmount == null)
      discountAmount = 0;
  }
}
