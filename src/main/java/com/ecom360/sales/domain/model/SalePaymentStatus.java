package com.ecom360.sales.domain.model;

/** Statut d'encaissement d'une vente : intégralement payée, acompte, ou rien encaissé. */
public final class SalePaymentStatus {

  public static final String PAID = "paid";
  public static final String PARTIAL = "partial";
  public static final String UNPAID = "unpaid";

  private SalePaymentStatus() {}

  public static boolean isValid(String value) {
    return PAID.equals(value) || PARTIAL.equals(value) || UNPAID.equals(value);
  }
}
