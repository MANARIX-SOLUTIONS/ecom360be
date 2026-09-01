package com.ecom360.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

class ThemeColorsTest {

  @Test
  void normalizeTrimsAndLowercases() {
    assertThat(ThemeColors.normalize("  #0F3460  ")).isEqualTo("#0f3460");
    assertThat(ThemeColors.normalize("")).isNull();
    assertThat(ThemeColors.normalize("   ")).isNull();
    assertThat(ThemeColors.normalize(null)).isNull();
  }

  @Test
  void requireValidHexRejectsMalformed() {
    assertThatThrownBy(() -> ThemeColors.requireValidHex("navy"))
        .isInstanceOf(BusinessRuleException.class);
    assertThatThrownBy(() -> ThemeColors.requireValidHex("#fff"))
        .isInstanceOf(BusinessRuleException.class);
    assertThatThrownBy(() -> ThemeColors.requireValidHex("#0f3460aa"))
        .isInstanceOf(BusinessRuleException.class);
    ThemeColors.requireValidHex("#0f3460");
    ThemeColors.requireValidHex("#0EA5E9");
  }

  @Test
  void navyPassesContrastAgainstWhite() {
    assertThat(ThemeColors.contrastAgainstWhite("#0f3460"))
        .isGreaterThan(ThemeColors.MIN_CONTRAST_WHITE);
    ThemeColors.requireContrastAgainstWhite("#0f3460");
  }

  @Test
  void yellowFailsContrastAgainstWhite() {
    assertThat(ThemeColors.contrastAgainstWhite("#ffff00"))
        .isLessThan(ThemeColors.MIN_CONTRAST_WHITE);
    assertThatThrownBy(() -> ThemeColors.requireContrastAgainstWhite("#ffff00"))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("trop claire");
  }
}
