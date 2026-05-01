package com.ecom360.shared.infrastructure.mail;

import org.springframework.web.util.HtmlUtils;

/**
 * Escapes plain text then converts newlines for safe {@code th:utext} blocks in
 * mail templates.
 */
public final class MailHtml {

  private MailHtml() {
  }

  /**
   * @return XSS-safe HTML snippet (no tags except &lt;br/&gt;) or empty string.
   */
  public static String escapedWithBr(String plain) {
    if (plain == null || plain.isBlank()) {
      return "";
    }
    return HtmlUtils.htmlEscape(plain.strip()).replace("\n", "<br/>");
  }
}
