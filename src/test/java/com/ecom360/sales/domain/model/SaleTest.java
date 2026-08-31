package com.ecom360.sales.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SaleTest {

  @Test
  void creditSaleStartsUnpaid() {
    Sale sale = saleOf(10_000, 0);
    assertThat(sale.getPaymentStatus()).isEqualTo(SalePaymentStatus.UNPAID);
    assertThat(sale.getRemainingAmount()).isEqualTo(10_000);
    assertThat(sale.hasOutstandingBalance()).isTrue();
  }

  @Test
  void depositMakesSalePartial() {
    Sale sale = saleOf(10_000, 4_000);
    assertThat(sale.getPaymentStatus()).isEqualTo(SalePaymentStatus.PARTIAL);
    assertThat(sale.getRemainingAmount()).isEqualTo(6_000);
  }

  @Test
  void fullPaymentMakesSalePaid() {
    Sale sale = saleOf(10_000, 10_000);
    assertThat(sale.getPaymentStatus()).isEqualTo(SalePaymentStatus.PAID);
    assertThat(sale.getRemainingAmount()).isZero();
    assertThat(sale.hasOutstandingBalance()).isFalse();
  }

  @Test
  void successiveInstallmentsSettleTheSale() {
    Sale sale = saleOf(10_000, 0);

    sale.applyPayment(3_000);
    assertThat(sale.getPaymentStatus()).isEqualTo(SalePaymentStatus.PARTIAL);
    assertThat(sale.getRemainingAmount()).isEqualTo(7_000);

    sale.applyPayment(7_000);
    assertThat(sale.getPaymentStatus()).isEqualTo(SalePaymentStatus.PAID);
    assertThat(sale.getRemainingAmount()).isZero();
  }

  @Test
  void remainingNeverGoesNegative() {
    Sale sale = saleOf(10_000, 0);
    sale.applyPayment(12_000);
    assertThat(sale.getRemainingAmount()).isZero();
    assertThat(sale.getPaymentStatus()).isEqualTo(SalePaymentStatus.PAID);
  }

  @Test
  void zeroTotalSaleIsPaid() {
    Sale sale = saleOf(0, 0);
    assertThat(sale.getPaymentStatus()).isEqualTo(SalePaymentStatus.PAID);
    assertThat(sale.hasOutstandingBalance()).isFalse();
  }

  private static Sale saleOf(int total, int amountPaid) {
    Sale sale = new Sale();
    sale.setStatus("completed");
    sale.setTotal(total);
    sale.setAmountPaid(amountPaid);
    sale.recomputePaymentStatus();
    return sale;
  }
}
