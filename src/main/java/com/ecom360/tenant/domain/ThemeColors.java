package com.ecom360.tenant.domain;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import java.util.regex.Pattern;

/** Validation of business theme hex colors (#RRGGBB) and contrast. */
public final class ThemeColors {

  private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

  /** WCAG AA for large text (3:1) against white button labels. */
  public static final double MIN_CONTRAST_WHITE = 3.0;

  private ThemeColors() {}

  public static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.toLowerCase();
  }

  public static boolean isValidHex(String hex) {
    return hex != null && HEX.matcher(hex).matches();
  }

  public static void requireValidHex(String hex) {
    if (!isValidHex(hex)) {
      throw new BusinessRuleException("Couleur invalide : utilisez le format #RRGGBB.");
    }
  }

  public static double contrastAgainstWhite(String hex) {
    requireValidHex(hex);
    int r = Integer.parseInt(hex.substring(1, 3), 16);
    int g = Integer.parseInt(hex.substring(3, 5), 16);
    int b = Integer.parseInt(hex.substring(5, 7), 16);
    double luminance = relativeLuminance(r, g, b);
    return 1.05 / (luminance + 0.05);
  }

  public static void requireContrastAgainstWhite(String hex) {
    if (contrastAgainstWhite(hex) < MIN_CONTRAST_WHITE) {
      throw new BusinessRuleException(
          "La couleur principale est trop claire pour le texte blanc (contraste insuffisant).");
    }
  }

  static double relativeLuminance(int r, int g, int b) {
    return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b);
  }

  private static double linearize(int channel) {
    double srgb = channel / 255.0;
    return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
  }
}
