package com.ecom360.sales.domain.model;

/** Nature d'un versement : acompte initial pris à la caisse, ou versement ultérieur. */
public final class SalePaymentKind {

  public static final String DEPOSIT = "deposit";
  public static final String INSTALLMENT = "installment";

  private SalePaymentKind() {}
}
