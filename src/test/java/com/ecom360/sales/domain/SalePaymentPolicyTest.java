package com.ecom360.sales.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecom360.client.domain.model.Client;
import com.ecom360.sales.domain.model.Sale;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

class SalePaymentPolicyTest {

  @Test
  void depositCannotBeNegative() {
    assertThatThrownBy(() -> SalePaymentPolicy.requireValidDeposit(-1, 5000))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(SalePaymentPolicy.DEPOSIT_NEGATIVE);
  }

  @Test
  void depositCannotExceedTotal() {
    assertThatThrownBy(() -> SalePaymentPolicy.requireValidDeposit(5001, 5000))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(SalePaymentPolicy.DEPOSIT_EXCEEDS_TOTAL);
  }

  @Test
  void depositBoundsAreInclusive() {
    assertThatCode(() -> SalePaymentPolicy.requireValidDeposit(0, 5000)).doesNotThrowAnyException();
    assertThatCode(() -> SalePaymentPolicy.requireValidDeposit(5000, 5000))
        .doesNotThrowAnyException();
  }

  @Test
  void outstandingBalanceRequiresNamedClient() {
    Client walkIn = new Client();
    walkIn.setName("Client comptoir");
    assertThatThrownBy(() -> SalePaymentPolicy.requireNamedClientForOutstanding(2000, walkIn))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(SalePaymentPolicy.NAMED_CLIENT_REQUIRED);
    assertThatThrownBy(() -> SalePaymentPolicy.requireNamedClientForOutstanding(2000, null))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(SalePaymentPolicy.NAMED_CLIENT_REQUIRED);
  }

  @Test
  void fullyPaidSaleAcceptsWalkIn() {
    Client walkIn = new Client();
    walkIn.setName("Client comptoir");
    assertThatCode(() -> SalePaymentPolicy.requireNamedClientForOutstanding(0, walkIn))
        .doesNotThrowAnyException();
  }

  @Test
  void paymentRejectedOnVoidedSale() {
    Sale sale = saleOf(5000, 0);
    sale.markVoided();
    assertThatThrownBy(() -> SalePaymentPolicy.requirePayment(sale, 1000))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(SalePaymentPolicy.SALE_NOT_COMPLETED);
  }

  @Test
  void paymentRejectedWhenAmountIsNotPositive() {
    Sale sale = saleOf(5000, 0);
    assertThatThrownBy(() -> SalePaymentPolicy.requirePayment(sale, 0))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(SalePaymentPolicy.AMOUNT_MIN);
  }

  @Test
  void paymentRejectedWhenNothingDue() {
    Sale sale = saleOf(5000, 5000);
    assertThatThrownBy(() -> SalePaymentPolicy.requirePayment(sale, 500))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(SalePaymentPolicy.NOTHING_DUE);
  }

  @Test
  void paymentRejectedWhenExceedingRemaining() {
    Sale sale = saleOf(5000, 2000);
    assertThatThrownBy(() -> SalePaymentPolicy.requirePayment(sale, 3001))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(SalePaymentPolicy.EXCEEDS_REMAINING);
    assertThatCode(() -> SalePaymentPolicy.requirePayment(sale, 3000)).doesNotThrowAnyException();
  }

  @Test
  void editCannotDropTotalBelowCollectedAmount() {
    assertThatThrownBy(() -> SalePaymentPolicy.requireTotalCoversPaid(1500, 2000))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(SalePaymentPolicy.PAID_EXCEEDS_NEW_TOTAL);
    assertThatCode(() -> SalePaymentPolicy.requireTotalCoversPaid(2000, 2000))
        .doesNotThrowAnyException();
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
