package com.ecom360.shared.infrastructure.mail;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Renders classpath templates under {@code templates/mail/} for transactional
 * emails.
 */
@Component
public class MailTemplateRenderer {

  private final SpringTemplateEngine templateEngine;

  public MailTemplateRenderer(SpringTemplateEngine templateEngine) {
    this.templateEngine = templateEngine;
  }

  /**
   * @param logicalName e.g. {@code mail/password-reset} (no suffix).
   */
  public String render(String logicalName, Context context) {
    return templateEngine.process(logicalName, context);
  }
}
