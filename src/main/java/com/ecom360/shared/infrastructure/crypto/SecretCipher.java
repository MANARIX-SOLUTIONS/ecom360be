package com.ecom360.shared.infrastructure.crypto;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM for secrets stored at rest (tenant PSP keys). The key is derived
 * with SHA-256 from {@code app.security.encryption-key}, so any sufficiently
 * long random string works. Changing the key makes existing values unreadable.
 */
@Component
public class SecretCipher {

  static final String PREFIX = "v1:";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public SecretCipher(@Value("${app.security.encryption-key:}") String rawKey) {
    this.key = rawKey == null || rawKey.isBlank() ? null : deriveKey(rawKey);
  }

  public boolean isConfigured() {
    return key != null;
  }

  public String encrypt(String plain) {
    if (plain == null) {
      return null;
    }
    requireKey();
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] out = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
      return PREFIX + Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new IllegalStateException("Secret encryption failed", e);
    }
  }

  public String decrypt(String stored) {
    if (stored == null) {
      return null;
    }
    if (!stored.startsWith(PREFIX)) {
      throw new IllegalStateException("Unsupported secret format");
    }
    requireKey();
    try {
      byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
      byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Secret decryption failed (wrong APP_ENCRYPTION_KEY?)", e);
    }
  }

  private void requireKey() {
    if (key == null) {
      throw new BusinessRuleException(
          "Chiffrement des secrets non configuré (APP_ENCRYPTION_KEY). Contactez le support.");
    }
  }

  private static SecretKeySpec deriveKey(String raw) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(raw.getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(digest, "AES");
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
