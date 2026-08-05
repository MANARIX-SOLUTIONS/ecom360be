package com.ecom360.supplier.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.supplier.domain.model.PurchaseOrder;
import org.junit.jupiter.api.Test;

class PurchaseOrderPaymentPolicyTest {

  @Test
  void depositCannotBeNegative() {
    assertThatThrownBy(() -> PurchaseOrderPaymentPolicy.requireValidDeposit(-1, 5000))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(PurchaseOrderPaymentPolicy.DEPOSIT_NEGATIVE);
  }

  @Test
  void depositCannotExceedTotal() {
    assertThatThrownBy(() -> PurchaseOrderPaymentPolicy.requireValidDeposit(5001, 5000))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(PurchaseOrderPaymentPolicy.DEPOSIT_EXCEEDS_TOTAL);
  }

  @Test
  void depositBoundsAreInclusive() {
    assertThatCode(() -> PurchaseOrderPaymentPolicy.requireValidDeposit(0, 5000))
        .doesNotThrowAnyException();
    assertThatCode(() -> PurchaseOrderPaymentPolicy.requireValidDeposit(5000, 5000))
        .doesNotThrowAnyException();
  }

  @Test
  void paymentRejectedWhenNotReceived() {
    PurchaseOrder po = poOf(5000, 0, "ordered");
    assertThatThrownBy(() -> PurchaseOrderPaymentPolicy.requirePayment(po, 1000))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(PurchaseOrderPaymentPolicy.NOT_RECEIVED);
  }

  @Test
  void paymentRejectedWhenAmountIsNotPositive() {
    PurchaseOrder po = poOf(5000, 0, "received");
    assertThatThrownBy(() -> PurchaseOrderPaymentPolicy.requirePayment(po, 0))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(PurchaseOrderPaymentPolicy.AMOUNT_MIN);
  }

  @Test
  void paymentRejectedWhenNothingDue() {
    PurchaseOrder po = poOf(5000, 5000, "received");
    assertThatThrownBy(() -> PurchaseOrderPaymentPolicy.requirePayment(po, 500))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(PurchaseOrderPaymentPolicy.NOTHING_DUE);
  }

  @Test
  void paymentRejectedWhenExceedingRemaining() {
    PurchaseOrder po = poOf(5000, 2000, "received");
    assertThatThrownBy(() -> PurchaseOrderPaymentPolicy.requirePayment(po, 3001))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(PurchaseOrderPaymentPolicy.EXCEEDS_REMAINING);
    assertThatCode(() -> PurchaseOrderPaymentPolicy.requirePayment(po, 3000))
        .doesNotThrowAnyException();
  }

  private static PurchaseOrder poOf(int total, int amountPaid, String status) {
    PurchaseOrder po = new PurchaseOrder();
    po.setStatus(status);
    po.setTotalAmount(total);
    po.setAmountPaid(amountPaid);
    po.recomputePaymentStatus();
    return po;
  }
}
