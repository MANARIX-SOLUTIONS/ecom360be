package com.ecom360.tenant.payment.infrastructure.paydunya;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaydunyaClientTest {

  @Test
  void sha512Hex_isStableAndLowerHex() {
    String hash = PaydunyaClient.sha512Hex("test-master-key");
    assertThat(hash).hasSize(128);
    assertThat(hash).matches("[0-9a-f]+");
    assertThat(PaydunyaClient.sha512Hex("test-master-key")).isEqualTo(hash);
  }

  @Test
  void toPaydunyaChannel_mapsWaveAndOrangeMoney() {
    PaydunyaClient client = new PaydunyaClient(
        new com.ecom360.tenant.payment.infrastructure.config.PaydunyaProperties(),
        new com.fasterxml.jackson.databind.ObjectMapper());
    assertThat(client.toPaydunyaChannel("wave")).isEqualTo("wave-senegal");
    assertThat(client.toPaydunyaChannel("orange_money")).isEqualTo("orange-money-senegal");
    assertThat(client.toPaydunyaChannel("other")).isNull();
  }
}
