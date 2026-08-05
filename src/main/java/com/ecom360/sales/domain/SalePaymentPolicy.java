package com.ecom360.sales.domain;

import com.ecom360.client.domain.model.Client;
import com.ecom360.sales.domain.model.Sale;
import com.ecom360.shared.domain.exception.BusinessRuleException;

/**
 * Règles du paiement par acompte : une vente peut n'être encaissée que
 * partiellement, le solde devient une créance sur un client nominatif. La vente à
 * crédit est le cas particulier d'un acompte à 0.
 */
public final class SalePaymentPolicy {

  public static final String AMOUNT_MIN = "Le montant du versement doit être supérieur à 0.";
  public static final String EXCEEDS_REMAINING =
      "Le versement ne peut pas dépasser le reste dû de la vente.";
  public static final String SALE_NOT_COMPLETED =
      "Seules les ventes validées peuvent recevoir un versement.";
  public static final String NOTHING_DUE = "Cette vente est déjà intégralement réglée.";
  public static final String NAMED_CLIENT_REQUIRED =
      "Pour laisser un reste à payer, sélectionnez un client nominatif.";
  public static final String DEPOSIT_EXCEEDS_TOTAL =
      "L'acompte ne peut pas dépasser le total de la vente.";
  public static final String DEPOSIT_NEGATIVE = "L'acompte ne peut pas être négatif.";
  public static final String PAID_EXCEEDS_NEW_TOTAL =
      "Le nouveau total est inférieur aux encaissements déjà enregistrés sur cette vente.";
  public static final String IMPORT_PARTIAL_FORBIDDEN =
      "Les paiements partiels ne sont pas disponibles pour les importations commerce.";

  private SalePaymentPolicy() {}

  /** Valide l'acompte saisi à la caisse au moment de créer ou modifier la vente. */
  public static void requireValidDeposit(int amountPaid, int total) {
    if (amountPaid < 0) {
      throw new BusinessRuleException(DEPOSIT_NEGATIVE);
    }
    if (amountPaid > total) {
      throw new BusinessRuleException(DEPOSIT_EXCEEDS_TOTAL);
    }
  }

  /**
   * Un reste à payer est une créance : il exige un client identifié, jamais le
   * client comptoir.
   */
  public static void requireNamedClientForOutstanding(int remaining, Client client) {
    if (remaining <= 0) {
      return;
    }
    if (client == null || client.isWalkIn()) {
      throw new BusinessRuleException(NAMED_CLIENT_REQUIRED);
    }
  }

  /** Valide un versement ultérieur imputé sur le solde d'une vente. */
  public static void requirePayment(Sale sale, int amount) {
    if (!sale.isCompleted()) {
      throw new BusinessRuleException(SALE_NOT_COMPLETED);
    }
    if (amount <= 0) {
      throw new BusinessRuleException(AMOUNT_MIN);
    }
    int remaining = sale.getRemainingAmount();
    if (remaining <= 0) {
      throw new BusinessRuleException(NOTHING_DUE);
    }
    if (amount > remaining) {
      throw new BusinessRuleException(EXCEEDS_REMAINING);
    }
  }

  /** Interdit de ramener le total sous les sommes déjà encaissées lors d'une édition. */
  public static void requireTotalCoversPaid(int newTotal, int amountPaid) {
    if (newTotal < amountPaid) {
      throw new BusinessRuleException(PAID_EXCEEDS_NEW_TOTAL);
    }
  }
}
