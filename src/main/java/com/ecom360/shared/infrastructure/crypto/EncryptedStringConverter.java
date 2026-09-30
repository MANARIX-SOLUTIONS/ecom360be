package com.ecom360.shared.infrastructure.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Instantiated by Hibernate through Spring's bean container, which is what
 * makes the constructor injection work. Use with {@code @Convert}.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

  private final SecretCipher cipher;

  public EncryptedStringConverter(SecretCipher cipher) {
    this.cipher = cipher;
  }

  @Override
  public String convertToDatabaseColumn(String attribute) {
    return attribute == null ? null : cipher.encrypt(attribute);
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    return dbData == null ? null : cipher.decrypt(dbData);
  }
}
