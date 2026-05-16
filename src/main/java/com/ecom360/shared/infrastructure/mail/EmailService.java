package com.ecom360.shared.infrastructure.mail;

import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Sends transactional emails (password reset, invitation). When SMTP is not
 * configured (MAIL_HOST
 * empty), logs the email instead of sending.
 */
@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final JavaMailSender mailSender;
  private final MailTemplateRenderer mailTemplates;
  private final String appUrl;
  private final String mailFrom;

  public EmailService(
      @Autowired(required = false) JavaMailSender mailSender,
      MailTemplateRenderer mailTemplates,
      @Value("${app.url:http://localhost:5173}") String appUrl,
      @Value("${spring.mail.from:noreply@ecom360.local}") String mailFrom) {
    this.mailSender = mailSender;
    this.mailTemplates = mailTemplates;
    this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    this.mailFrom = mailFrom;
  }

  private Context mailContext(String preheader) {
    Context ctx = new Context(Locale.FRENCH);
    ctx.setVariable("appUrl", appUrl);
    ctx.setVariable("preheader", preheader);
    return ctx;
  }

  public void sendPasswordResetEmail(String to, String resetLink) {
    String subject = "360 PME Commerce | Réinitialisation de votre mot de passe";
    String plain = """
        Bonjour,

        Nous avons reçu une demande de réinitialisation de mot de passe pour votre compte
        360 PME Commerce.

        Pour définir un nouveau mot de passe (lien valable 24h), cliquez ici :
        %s

        Si vous n’êtes pas à l’origine de cette demande, vous pouvez ignorer ce message
        en toute sécurité.

        Cordialement,
        L’équipe 360 PME Commerce
        """
        .formatted(resetLink);

    Context ctx = mailContext("Réinitialisation — lien valable 24 h.");
    ctx.setVariable("resetLink", resetLink);
    send(to, subject, plain, mailTemplates.render("mail/password-reset", ctx));
  }

  /**
   * Accusé de réception d'une demande de démo (PME / Afrique : délai annoncé).
   */
  public void sendDemoRequestReceivedEmail(String to, String fullName, String businessName) {
    String subject = "360 PME Commerce | Demande de démo confirmée";
    String plain = """
        Bonjour %s,

        Merci pour votre intérêt.
        Votre demande de démo pour « %s » a bien été enregistrée.

        Notre équipe l’examine sous 48h ouvrées (souvent sous 24h).
        Dès validation, vous recevrez un email d’activation avec :
        - un lien pour définir votre mot de passe,
        - l’accès à votre essai gratuit.

        Cordialement,
        L’équipe 360 PME Commerce
        """
        .formatted(fullName, businessName);

    Context ctx = mailContext("Votre demande de démo a bien été reçue.");
    ctx.setVariable("greetingLine", "Bonjour " + fullName + ",");
    ctx.setVariable(
        "ackLine",
        "Merci pour votre intérêt. La demande de démo pour « %s » a été enregistrée."
            .formatted(businessName));
    ctx.setVariable(
        "followUpLine",
        "Notre équipe examine votre dossier sous 48 h ouvrées (souvent moins de "
            + "24 h). Après validation, vous recevrez un email d’activation avec un "
            + "lien pour définir votre mot de passe et accéder à votre essai gratuit.");
    ctx.setVariable(
        "bulletLines",
        List.of(
            "Un lien sécurisé pour définir votre mot de passe", "L’accès à votre essai gratuit"));
    send(to, subject, plain, mailTemplates.render("mail/demo-request-received", ctx));
  }

  /** Refus de demande démo (motif optionnel). */
  public void sendDemoRequestRejectedEmail(String to, String fullName, String reasonOrNull) {
    String subject = "360 PME Commerce | Suite à votre demande de démo";
    String extra = reasonOrNull != null && !reasonOrNull.isBlank()
        ? "\n\nMotif : " + reasonOrNull + "\n"
        : "\n";
    String plain = """
        Bonjour %s,

        Nous ne pouvons pas donner suite à votre demande de démo pour le moment.%s
        Si besoin, notre équipe reste disponible pour vous orienter vers la meilleure
        option de démarrage.

        Cordialement,
        L’équipe 360 PME Commerce
        """
        .formatted(fullName, extra);

    boolean hasReason = reasonOrNull != null && !reasonOrNull.isBlank();
    Context ctx = mailContext("Réponse suite à votre demande de démo.");
    ctx.setVariable("greetingLine", "Bonjour " + fullName + ",");
    ctx.setVariable("hasReason", hasReason);
    ctx.setVariable("reasonLine", hasReason ? "Motif : " + reasonOrNull.strip() : "");
    send(to, subject, plain, mailTemplates.render("mail/demo-request-rejected", ctx));
  }

  public void sendInvitationEmail(
      String to, String fullName, String businessName, String setPasswordLink) {
    String subject = "360 PME Commerce | Invitation à rejoindre " + businessName;
    String plain = """
        Bonjour %s,

        Vous avez été invité(e) à rejoindre « %s » sur 360 PME Commerce.

        Pour activer votre accès et définir votre mot de passe (lien valable 24h),
        utilisez le lien suivant :
        %s

        Une fois connecté(e), vous pourrez accéder aux espaces et fonctionnalités qui
        vous ont été attribués.

        Cordialement,
        L’équipe 360 PME Commerce
        """
        .formatted(fullName, businessName, setPasswordLink);

    Context ctx = mailContext("Activez votre accès 360 PME Commerce — valable 24 h.");
    ctx.setVariable("greetingLine", "Bonjour " + fullName + ",");
    ctx.setVariable("inviteLine", "Rejoignez « %s » sur 360 PME Commerce.".formatted(businessName));
    ctx.setVariable("setPasswordLink", setPasswordLink);
    send(to, subject, plain, mailTemplates.render("mail/invitation", ctx));
  }

  private void send(String to, String subject, String plainText, String htmlBody) {
    if (mailSender != null) {
      try {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setFrom(mailFrom);
        helper.setText(plainText, htmlBody);
        mailSender.send(msg);
        log.info("Email sent to {}", to);
      } catch (Exception e) {
        log.warn("Failed to send email to {}: {} — falling back to log", to, e.getMessage());
        logEmail(to, subject, plainText);
      }
    } else {
      logEmail(to, subject, plainText);
    }
  }

  private void logEmail(String to, String subject, String body) {
    log.info("Email (SMTP not configured) — To: {}, Subject: {}\n---\n{}\n---", to, subject, body);
  }

  public String buildResetPasswordLink(String rawToken) {
    return appUrl + "/reset-password?token=" + rawToken;
  }

  public String buildSubscriptionSettingsLink() {
    return appUrl + "/settings/subscription";
  }

  /** Base app URL for post-login links (SPA home). */
  public String buildAppHomeLink() {
    return appUrl + "/";
  }

  public void sendSubscriptionPeriodReminderEmail(
      String to,
      String fullName,
      boolean trialing,
      boolean cancellingAtPeriodEnd,
      int daysLeft,
      String periodEndFormattedFr,
      String subscriptionUrl) {
    String subject;
    String plain;
    String heading;
    String detailLine;
    String ctaLabel;

    if (cancellingAtPeriodEnd) {
      subject = "360 PME Commerce | Fin d’accès programmée au " + periodEndFormattedFr;
      plain = """
          Bonjour %s,

          Conformément à votre demande, l’accès à votre abonnement prendra fin le %s.

          Si vous souhaitez maintenir votre activité sans interruption, vous pouvez
          réactiver ou ajuster votre offre ici :
          %s

          Cordialement,
          L’équipe 360 PME Commerce
          """
          .formatted(fullName, periodEndFormattedFr, subscriptionUrl);
      heading = "Fin d’accès programmée";
      detailLine = "Conformément à votre demande, l’accès prendra fin le " + periodEndFormattedFr + ".";
      ctaLabel = "Gérer ou réactiver mon abonnement";
    } else if (trialing) {
      subject = daysLeft == 1
          ? "360 PME Commerce | Votre essai se termine demain"
          : "360 PME Commerce | Il reste %d jours d’essai".formatted(daysLeft);
      plain = """
          Bonjour %s,

          Votre essai gratuit se termine le %s.
          Pour continuer à utiliser la plateforme sans interruption, choisissez votre plan :

          %s

          Cordialement,
          L’équipe 360 PME Commerce
          """
          .formatted(fullName, periodEndFormattedFr, subscriptionUrl);
      heading = daysLeft == 1
          ? "Votre essai se termine demain"
          : "Plus que %d jours d’essai".formatted(daysLeft);
      detailLine = "Votre essai gratuit se termine le " + periodEndFormattedFr + ".";
      ctaLabel = "Choisir un plan avant la date limite";
    } else {
      subject = daysLeft == 1
          ? "360 PME Commerce | Fin de période d’abonnement demain"
          : "360 PME Commerce | Fin de période dans %d jours".formatted(daysLeft);
      plain = """
          Bonjour %s,

          Votre période d’abonnement arrive à échéance le %s.
          Pour éviter toute interruption, vérifiez ou renouvelez votre abonnement :

          %s

          Cordialement,
          L’équipe 360 PME Commerce
          """
          .formatted(fullName, periodEndFormattedFr, subscriptionUrl);
      heading = daysLeft == 1
          ? "Fin de votre période actuelle demain"
          : "Renouvellement dans %d jours".formatted(daysLeft);
      detailLine = "La période en cours se clôt le " + periodEndFormattedFr + ".";
      ctaLabel = "Vérifier ou renouveler l’abonnement";
    }

    String pre = subject.length() > 90 ? subject.substring(0, 87) + "…" : subject;

    Context ctx = mailContext(pre);
    ctx.setVariable("heading", heading);
    ctx.setVariable("greetingLine", "Bonjour " + fullName + ",");
    ctx.setVariable("detailLine", detailLine);
    ctx.setVariable("subscriptionUrl", subscriptionUrl);
    ctx.setVariable("ctaLabel", ctaLabel);
    send(to, subject, plain, mailTemplates.render("mail/subscription-reminder", ctx));
  }

  public void sendWelcomeAfterSignupEmail(
      String to,
      String fullName,
      String businessName,
      String trialEndFormattedFrOrNull,
      String dashboardUrl) {
    boolean trial = trialEndFormattedFrOrNull != null && !trialEndFormattedFrOrNull.isBlank();
    String subject = trial
        ? "360 PME Commerce | Bienvenue, votre essai démarre"
        : "360 PME Commerce | Bienvenue sur votre espace";
    String trialBlock = trial
        ? "\nUn essai gratuit est actif jusqu’au %s.".formatted(trialEndFormattedFrOrNull)
        : "\nVotre abonnement est actif.";
    String plain = """
        Bonjour %s,

        Bienvenue sur 360 PME Commerce pour « %s ».%s

        Pour bien démarrer :
        • créez votre premier magasin,
        • ajoutez vos produits et catégories,
        • enregistrez vos premières ventes.

        Gérer votre abonnement :
        %s

        Accéder à votre tableau de bord :
        %s

        Cordialement,
        L’équipe 360 PME Commerce
        """
        .formatted(
            fullName, businessName, trialBlock, buildSubscriptionSettingsLink(), dashboardUrl);

    String trialLine = trial
        ? "Un essai gratuit est actif jusqu’au " + trialEndFormattedFrOrNull + "."
        : "Votre abonnement est actif.";

    Context ctx = mailContext("Bienvenue sur 360 PME Commerce — premiers pas.");
    ctx.setVariable("greetingLine", "Bonjour " + fullName + ",");
    ctx.setVariable("readyLine", "Votre espace « " + businessName + " » est prêt.");
    ctx.setVariable("trialOrActiveLine", trialLine);
    ctx.setVariable("subscriptionUrl", buildSubscriptionSettingsLink());
    ctx.setVariable("dashboardUrl", dashboardUrl);
    ctx.setVariable(
        "starterLines",
        List.of(
            "Créez votre premier magasin",
            "Ajoutez produits et catégories",
            "Enregistrez vos premières ventes"));

    send(to, subject, plain, mailTemplates.render("mail/welcome-signup", ctx));
  }

  public void sendBillingReminderEmail(
      String to, String fullName, String subject, String fullBody) {
    String greeting = fullName != null && !fullName.isBlank()
        ? "Bonjour %s,\n\n".formatted(fullName)
        : "Bonjour,\n\n";
    String plain = greeting + fullBody.strip() + "\n\nCordialement,\nL’équipe 360 PME Commerce";

    String pre = subject.length() > 90 ? subject.substring(0, 87) + "…" : subject;

    Context ctx = mailContext(pre);
    ctx.setVariable(
        "greetingLine",
        fullName != null && !fullName.isBlank() ? "Bonjour " + fullName + "," : "Bonjour,");
    ctx.setVariable("bodyHtml", MailHtml.escapedWithBr(fullBody.strip()));
    send(to, subject, plain, mailTemplates.render("mail/billing-reminder", ctx));
  }

  public void sendMaintenanceNoticeEmail(
      String to,
      String businessName,
      String title,
      String message,
      String maintenanceWindowOrBlank,
      String statusPageUrl) {
    String window = maintenanceWindowOrBlank != null && !maintenanceWindowOrBlank.isBlank()
        ? maintenanceWindowOrBlank + "\n\n"
        : "";
    String subject = "360 PME Commerce — " + title;
    String plain = """
        Bonjour %s,

        %s

        %s
        Pour suivre l’état du service en temps réel :
        %s

        Merci pour votre compréhension.
        Cordialement,
        L’équipe 360 PME Commerce
        """
        .formatted(
            businessName != null && !businessName.isBlank() ? businessName : "équipe",
            message,
            window,
            statusPageUrl != null && !statusPageUrl.isBlank()
                ? statusPageUrl
                : buildAppHomeLink());

    String salute = businessName != null && !businessName.isBlank() ? businessName : "équipe";
    boolean hasWindow = maintenanceWindowOrBlank != null && !maintenanceWindowOrBlank.isBlank();
    String link = statusPageUrl != null && !statusPageUrl.isBlank() ? statusPageUrl : buildAppHomeLink();

    Context ctx = mailContext(title);
    ctx.setVariable("title", title);
    ctx.setVariable("greetingLine", "Bonjour " + salute + ",");
    ctx.setVariable("messageHtml", MailHtml.escapedWithBr(message));
    ctx.setVariable("hasWindow", hasWindow);
    ctx.setVariable("windowText", hasWindow ? maintenanceWindowOrBlank.strip() : "");
    ctx.setVariable("statusLink", link);
    send(to, subject, plain, mailTemplates.render("mail/maintenance-notice", ctx));
  }
}
