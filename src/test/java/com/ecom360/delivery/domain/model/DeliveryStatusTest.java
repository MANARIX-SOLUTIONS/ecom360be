package com.ecom360.delivery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DeliveryStatusTest {

  @Test
  void fromAcceptsKnownStatusesCaseInsensitively() {
    assertThat(DeliveryStatus.from("delivered"))
        .isEqualTo(DeliveryStatus.delivered);
    assertThat(DeliveryStatus.from("FAILED")).isEqualTo(DeliveryStatus.failed);
    assertThat(DeliveryStatus.from(" cancelled "))
        .isEqualTo(DeliveryStatus.cancelled);
  }

  @Test
  void fromRejectsUnknownOrBlank() {
    assertThatThrownBy(() -> DeliveryStatus.from("out"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DeliveryStatus.from(" "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DeliveryStatus.from(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
