package com.ecom360.shared.infrastructure.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (password reset, invitation).
 * When SMTP is not configured (MAIL_HOST empty), logs the email instead of sending.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String appUrl;
    private final String mailFrom;

    public EmailService(
            @org.springframework.beans.factory.annotation.Autowired(required = false) JavaMailSender mailSender,
            @Value("${app.url:http://localhost:5173}") String appUrl,
            @Value("${spring.mail.from:noreply@ecom360.local}") String mailFrom) {
        this.mailSender = mailSender;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        this.mailFrom = mailFrom;
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "360 PME Commerce — Réinitialisation du mot de passe";
        String body = """
            Bonjour,

            Vous avez demandé la réinitialisation de votre mot de passe.

            Cliquez sur le lien ci-dessous pour définir un nouveau mot de passe (valide 24h) :

            %s

            Si vous n'avez pas fait cette demande, ignorez cet email.

            — L'équipe 360 PME Commerce
            """.formatted(resetLink);

        send(to, subject, body);
    }

    public void sendInvitationEmail(String to, String fullName, String businessName, String setPasswordLink) {
        String subject = "360 PME Commerce — Invitation à rejoindre " + businessName;
        String body = """
            Bonjour %s,

            Vous avez été invité à rejoindre %s sur 360 PME Commerce.

            Cliquez sur le lien ci-dessous pour définir votre mot de passe et accéder à votre espace (lien valide 24h) :

            %s

            — L'équipe 360 PME Commerce
            """.formatted(fullName, businessName, setPasswordLink);

        send(to, subject, body);
    }

    private void send(String to, String subject, String body) {
        if (mailSender != null) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setTo(to);
                msg.setSubject(subject);
                msg.setText(body);
                msg.setFrom(mailFrom);
                mailSender.send(msg);
                log.info("Email sent to {}", to);
            } catch (Exception e) {
                log.warn("Failed to send email to {}: {} — falling back to log", to, e.getMessage());
                logEmail(to, subject, body);
            }
        } else {
            logEmail(to, subject, body);
        }
    }

    private void logEmail(String to, String subject, String body) {
        log.info("Email (SMTP not configured) — To: {}, Subject: {}\n---\n{}\n---", to, subject, body);
    }

    public String buildResetPasswordLink(String rawToken) {
        return appUrl + "/reset-password?token=" + rawToken;
    }
}
