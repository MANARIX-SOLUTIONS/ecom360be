package com.ecom360.client.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecom360.client.domain.model.Client;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

class ClientCreditPolicyTest {

  @Test
  void walkInAliasesMatchPosClient() {
    assertThat(ClientCreditPolicy.isWalkInName("Client comptoir")).isTrue();
    assertThat(ClientCreditPolicy.isWalkInName("  WALK-IN ")).isTrue();
    assertThat(ClientCreditPolicy.isWalkInName("walk in")).isTrue();
    assertThat(ClientCreditPolicy.isWalkInName("Client anonyme")).isTrue();
    assertThat(ClientCreditPolicy.isWalkInName("Fatou Diallo")).isFalse();
    assertThat(ClientCreditPolicy.isWalkInName(null)).isFalse();
  }

  @Test
  void namedClientRequiredForCreditWalkIn() {
    Client walkIn = new Client();
    walkIn.setName("Client comptoir");
    assertThatThrownBy(() -> ClientCreditPolicy.requireNamedClientForCredit("credit", walkIn))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(ClientCreditPolicy.NAMED_CLIENT_REQUIRED);
  }

  @Test
  void cashAllowsWalkIn() {
    Client walkIn = new Client();
    walkIn.setName("Client comptoir");
    ClientCreditPolicy.requireNamedClientForCredit("cash", walkIn);
  }

  @Test
  void namedClientAllowedForCredit() {
    Client named = new Client();
    named.setName("Fatou Diallo");
    ClientCreditPolicy.requireNamedClientForCredit("credit", named);
  }

  @Test
  void featureMustBeEnabled() {
    assertThatThrownBy(() -> ClientCreditPolicy.requireFeatureEnabled(false))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(ClientCreditPolicy.PLAN_REQUIRED);
    assertThatThrownBy(() -> ClientCreditPolicy.requireFeatureEnabled(null))
        .isInstanceOf(BusinessRuleException.class);
    ClientCreditPolicy.requireFeatureEnabled(true);
  }

  @Test
  void repaymentRejectedWhenNothingOwed() {
    Client c = namedClientWithBalance(0);
    assertThatThrownBy(() -> ClientCreditPolicy.requireRepayment(c, 1000))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(ClientCreditPolicy.NOTHING_OWED);
  }

  @Test
  void repaymentRejectedWhenOverpaying() {
    Client c = namedClientWithBalance(500);
    assertThatThrownBy(() -> ClientCreditPolicy.requireRepayment(c, 501))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(ClientCreditPolicy.EXCEEDS_BALANCE);
  }

  @Test
  void repaymentRejectedForWalkIn() {
    Client c = new Client();
    c.setName("Client comptoir");
    c.setCreditBalance(2000);
    assertThatThrownBy(() -> ClientCreditPolicy.requireRepayment(c, 500))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage(ClientCreditPolicy.WALK_IN_NO_PAYMENT);
  }

  @Test
  void repaymentAllowedUpToBalance() {
    Client c = namedClientWithBalance(1500);
    ClientCreditPolicy.requireRepayment(c, 1500);
  }

  @Test
  void addAndDeductCredit() {
    Client c = namedClientWithBalance(0);
    c.addCredit(3000);
    assertThat(c.getCreditBalance()).isEqualTo(3000);
    c.deductCredit(1200);
    assertThat(c.getCreditBalance()).isEqualTo(1800);
  }

  private static Client namedClientWithBalance(int balance) {
    Client c = new Client();
    c.setName("Fatou Diallo");
    c.setCreditBalance(balance);
    return c;
  }
}
