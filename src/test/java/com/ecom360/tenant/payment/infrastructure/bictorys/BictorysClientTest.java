package com.ecom360.tenant.payment.infrastructure.bictorys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BictorysClientTest {

  private static final String SECRET = "whsec_test";
  private static final String BODY = "{\"id\":\"tx_1\",\"status\":\"succeeded\"}";

  BictorysClient client;

  @BeforeEach
  void setUp() {
    BictorysProperties props = new BictorysProperties();
    props.setEnabled(true);
    props.setApiKey("test_public-key");
    props.setWebhookSecret(SECRET);
    client = new BictorysClient(props, new ObjectMapper());
  }

  @Test
  void toPaymentType_mapsChannels() {
    assertThat(BictorysClient.toPaymentType("wave")).isEqualTo("wave_money");
    assertThat(BictorysClient.toPaymentType("orange_money")).isEqualTo("orange_money");
    assertThat(BictorysClient.toPaymentType("card")).isNull();
    assertThat(BictorysClient.toPaymentType(null)).isNull();
  }

  @Test
  void normalizePhone_formatsSenegaleseNumbers() {
    assertThat(BictorysClient.normalizePhone("77 123 45 67")).isEqualTo("+221771234567");
    assertThat(BictorysClient.normalizePhone("221771234567")).isEqualTo("+221771234567");
    assertThat(BictorysClient.normalizePhone("+221 77 123 45 67")).isEqualTo("+221771234567");
    assertThat(BictorysClient.normalizePhone("00221771234567")).isEqualTo("+221771234567");
    assertThat(BictorysClient.normalizePhone("123")).isNull();
    assertThat(BictorysClient.normalizePhone(" ")).isNull();
  }

  @Test
  void isPublicUrl_rejectsLocalAndPrivateHosts() {
    assertThat(BictorysClient.isPublicUrl("https://app.ecom360.sn/settings?x=1")).isTrue();
    assertThat(BictorysClient.isPublicUrl("https://8.8.8.8/ok")).isTrue();
    assertThat(BictorysClient.isPublicUrl("http://localhost:5173/settings")).isFalse();
    assertThat(BictorysClient.isPublicUrl("http://127.0.0.1:4200/x")).isFalse();
    assertThat(BictorysClient.isPublicUrl("http://192.168.1.10:4200/x")).isFalse();
    assertThat(BictorysClient.isPublicUrl("http://10.0.0.5/x")).isFalse();
    assertThat(BictorysClient.isPublicUrl("http://[::1]:8080/x")).isFalse();
    assertThat(BictorysClient.isPublicUrl("ftp://example.com/x")).isFalse();
    assertThat(BictorysClient.isPublicUrl(null)).isFalse();
  }

  @Test
  void verifyWebhook_acceptsValidHmac() {
    String ts = String.valueOf(System.currentTimeMillis());
    String sig = BictorysClient.hmacSha256Hex(SECRET, ts + "." + BODY);

    assertThat(client.verifyWebhook(BODY, null, sig, ts)).isTrue();
  }

  @Test
  void verifyWebhook_rejectsTamperedBodyOrStaleTimestamp() {
    String ts = String.valueOf(System.currentTimeMillis());
    String sig = BictorysClient.hmacSha256Hex(SECRET, ts + "." + BODY);
    assertThat(client.verifyWebhook(BODY + " ", null, sig, ts)).isFalse();

    String stale = String.valueOf(System.currentTimeMillis() - 10 * 60 * 1000L);
    String staleSig = BictorysClient.hmacSha256Hex(SECRET, stale + "." + BODY);
    assertThat(client.verifyWebhook(BODY, null, staleSig, stale)).isFalse();
  }

  @Test
  void verifyWebhook_staticSecretFallback() {
    assertThat(client.verifyWebhook(BODY, SECRET, null, null)).isTrue();
    assertThat(client.verifyWebhook(BODY, "wrong", null, null)).isFalse();
    assertThat(client.verifyWebhook(BODY, null, null, null)).isFalse();
  }

  @Test
  void verifyWebhook_withCredentials_usesThatAccountSecret() {
    BictorysCredentials accountA = BictorysCredentials.forEnvironment(false, "key_a", "secret_a", "SN");
    BictorysCredentials accountB = BictorysCredentials.forEnvironment(false, "key_b", "secret_b", "SN");
    String ts = String.valueOf(System.currentTimeMillis());
    String sigB = BictorysClient.hmacSha256Hex("secret_b", ts + "." + BODY);

    assertThat(client.verifyWebhook(accountB, BODY, null, sigB, ts)).isTrue();
    assertThat(client.verifyWebhook(accountA, BODY, null, sigB, ts)).isFalse();
    // The platform secret must not validate a business account either.
    String platformSig = BictorysClient.hmacSha256Hex(SECRET, ts + "." + BODY);
    assertThat(client.verifyWebhook(accountA, BODY, null, platformSig, ts)).isFalse();
  }

  @Test
  void verifyWebhook_withCredentials_rejectsMissingSecret() {
    BictorysCredentials noSecret = BictorysCredentials.forEnvironment(true, "key", null, null);

    assertThat(client.verifyWebhook(noSecret, BODY, "anything", null, null)).isFalse();
  }

  @Test
  void forEnvironment_selectsBaseUrlAndDefaultCountry() {
    BictorysCredentials live =
        BictorysCredentials.forEnvironment(true, "sk_live_x", "whsec_y", null);
    BictorysCredentials test = BictorysCredentials.forEnvironment(false, "k", "s", "CI");

    assertThat(live.baseUrl()).isEqualTo(BictorysCredentials.LIVE_BASE_URL);
    assertThat(live.country()).isEqualTo("SN");
    assertThat(test.baseUrl()).isEqualTo(BictorysCredentials.TEST_BASE_URL);
    assertThat(test.country()).isEqualTo("CI");
    assertThat(live.toString()).doesNotContain("sk_live_x").doesNotContain("whsec_y");
  }

  @Test
  void createCharge_withCredentials_requiresApiKey() {
    BictorysCredentials noKey = BictorysCredentials.forEnvironment(false, " ", "s", "SN");

    assertThatThrownBy(() -> client.createCharge(
            noKey, 1000, "wave", "ref", "https://app.example.com/pos", null, null, null, null))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void verifyWebhook_rejectsWhenSecretNotConfigured() {
    BictorysProperties props = new BictorysProperties();
    BictorysClient unconfigured = new BictorysClient(props, new ObjectMapper());

    assertThat(unconfigured.verifyWebhook(BODY, "", null, null)).isFalse();
  }
}
