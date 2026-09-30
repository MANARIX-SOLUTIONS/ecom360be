package com.ecom360.shared.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

  private final SecretCipher cipher = new SecretCipher("unit-test-encryption-key");

  @Test
  void encryptThenDecrypt_roundTrips() {
    String stored = cipher.encrypt("sk_live_abc123");

    assertThat(stored).startsWith("v1:").doesNotContain("sk_live_abc123");
    assertThat(cipher.decrypt(stored)).isEqualTo("sk_live_abc123");
  }

  @Test
  void encrypt_usesRandomIv() {
    assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
  }

  @Test
  void nullStaysNull() {
    assertThat(cipher.encrypt(null)).isNull();
    assertThat(cipher.decrypt(null)).isNull();
  }

  @Test
  void decrypt_rejectsTamperedCiphertext() {
    String stored = cipher.encrypt("secret");
    byte[] raw = Base64.getDecoder().decode(stored.substring(3));
    raw[raw.length - 1] ^= 1;
    String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

    assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void decrypt_failsWithAnotherKey() {
    String stored = cipher.encrypt("secret");

    assertThatThrownBy(() -> new SecretCipher("another-key").decrypt(stored))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void withoutKey_refusesToEncrypt() {
    SecretCipher unconfigured = new SecretCipher("");

    assertThat(unconfigured.isConfigured()).isFalse();
    assertThatThrownBy(() -> unconfigured.encrypt("x")).isInstanceOf(BusinessRuleException.class);
  }
}
