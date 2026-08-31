package com.ecom360.supplier.domain;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.supplier.domain.model.PurchaseOrder;

/**
 * Règles du paiement par acompte sur un bon de commande : à la réception, une
 * partie peut être versée au fournisseur, le solde reste une dette. Un acompte
 * omis (null ou 0) conserve le comportement historique : dette égale au total.
 */
public final class PurchaseOrderPaymentPolicy {

  public static final String AMOUNT_MIN = "Le montant du versement doit être supérieur à 0.";
  public static final String EXCEEDS_REMAINING =
      "Le versement ne peut pas dépasser le reste dû du bon de commande.";
  public static final String NOT_RECEIVED =
      "Seuls les bons de commande réceptionnés peuvent recevoir un versement.";
  public static final String NOTHING_DUE = "Ce bon de commande est déjà intégralement réglé.";
  public static final String DEPOSIT_EXCEEDS_TOTAL =
      "L'acompte ne peut pas dépasser le total du bon de commande.";
  public static final String DEPOSIT_NEGATIVE = "L'acompte ne peut pas être négatif.";

  private PurchaseOrderPaymentPolicy() {}

  /** Valide l'acompte saisi à la réception. */
  public static void requireValidDeposit(int amountPaid, int total) {
    if (amountPaid < 0) {
      throw new BusinessRuleException(DEPOSIT_NEGATIVE);
    }
    if (amountPaid > total) {
      throw new BusinessRuleException(DEPOSIT_EXCEEDS_TOTAL);
    }
  }

  /** Valide un versement ultérieur imputé sur le solde d'un bon réceptionné. */
  public static void requirePayment(PurchaseOrder po, int amount) {
    if (!po.isReceived()) {
      throw new BusinessRuleException(NOT_RECEIVED);
    }
    if (amount <= 0) {
      throw new BusinessRuleException(AMOUNT_MIN);
    }
    int remaining = po.getRemainingAmount();
    if (remaining <= 0) {
      throw new BusinessRuleException(NOTHING_DUE);
    }
    if (amount > remaining) {
      throw new BusinessRuleException(EXCEEDS_REMAINING);
    }
  }
}
