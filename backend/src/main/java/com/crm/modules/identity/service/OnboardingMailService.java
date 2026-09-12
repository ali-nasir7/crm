package com.crm.modules.identity.service;

import com.crm.config.CrmProperties;
import com.crm.modules.email.domain.EmailAccount;
import com.crm.modules.email.repo.EmailAccountRepository;
import com.crm.modules.email.service.EmailProvider;
import com.crm.modules.email.service.SmtpEmailProvider;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.UUID;

/**
 * Sends credential emails (onboarding + admin-initiated reset) for admin-created users.
 * Credentials are NEVER logged and never stored in plaintext.
 *
 * Sender resolution order (brief: "send from the configured CRM sender"):
 *   1. The organization's VERIFIED SMTP account configured in the UI (Emails > Accounts) -
 *      this is the normal path for a self-hosted personal CRM: the admin connects
 *      hazeljones.cse@gmail.com once in the UI, every credential email goes out through it.
 *   2. Fallback: the environment SMTP config (CRM_MAIL_HOST/PORT/USERNAME/PASSWORD/FROM).
 * If BOTH fail, the caller decides: user creation still succeeds (admin gets the temp
 * password in the API response to relay manually); reset keeps the honest 422.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingMailService {

    private final CrmProperties props;
    private final EmailAccountRepository emailAccounts;
    private final com.crm.common.util.EncryptionService encryption;
    private final SmtpEmailProvider smtpProvider;

    public boolean sendOnboarding(UUID orgId, String toEmail, String fullName, String tempPassword, String orgName) {
        String safeName = fullName == null || fullName.isBlank() ? toEmail : fullName;
        String body = "Hi " + safeName + ",\n\n"
            + "An account has been created for you on " + props.app().name() + (orgName == null ? "" : " (" + orgName + ")") + ".\n\n"
            + "Login URL: " + props.app().appUrl() + "/login\n"
            + "Email: " + toEmail + "\n"
            + "Temporary password: " + tempPassword + "\n\n"
            + "For security you must choose your own password at first login. "
            + "This temporary password stops working as soon as you set a new one.\n\n"
            + "- " + props.app().name();
        return send(orgId, toEmail, "Your " + props.app().name() + " account is ready", body);
    }

    public boolean sendPasswordReset(UUID orgId, String toEmail, String fullName, String tempPassword) {
        String safeName = fullName == null || fullName.isBlank() ? toEmail : fullName;
        String body = "Hi " + safeName + ",\n\n"
            + "An administrator reset your " + props.app().name() + " password.\n\n"
            + "Login URL: " + props.app().appUrl() + "/login\n"
            + "Email: " + toEmail + "\n"
            + "Temporary password: " + tempPassword + "\n\n"
            + "You must choose your own password at first login. "
            + "This temporary password stops working as soon as you set a new one.\n\n"
            + "- " + props.app().name();
        return send(orgId, toEmail, "Your " + props.app().name() + " password was reset", body);
    }

    /** UI-configured VERIFIED account first, env SMTP fallback. Returns false when both fail. */
    private boolean send(UUID orgId, String toEmail, String subject, String bodyText) {
        if (orgId != null) {
            EmailAccount account = emailAccounts
                .findFirstByOrganizationIdAndProviderAndStatusOrderByCreatedAtAsc(orgId, EmailAccount.Provider.SMTP, "VERIFIED")
                .orElse(null);
            if (account != null) {
                try {
                    smtpProvider.send(new EmailProvider.SendCommand(
                        account.getEmail(), account.getDisplayName(), account.getReplyTo(),
                        account.getSmtpHost(), account.getSmtpPort() == null ? 587 : account.getSmtpPort(),
                        account.getSmtpEncryption(), account.getSmtpUsername(),
                        account.getSmtpPasswordEnc() == null ? null : encryption.decrypt(account.getSmtpPasswordEnc()),
                        java.util.List.of(toEmail), java.util.List.of(), subject, null, bodyText));
                    return true;
                } catch (Exception e) {
                    // Deliberately generic: no credentials, no internals with sensitive data.
                    log.warn("Credential email to {} via UI-configured sender failed ({}); trying env SMTP fallback",
                        toEmail, e.getClass().getSimpleName());
                }
            }
        }
        return sendViaEnvSmtp(toEmail, subject, bodyText);
    }

    /** Legacy/env path: SMTP from CRM_MAIL_* environment properties. */
    private boolean sendViaEnvSmtp(String toEmail, String subject, String bodyText) {
        CrmProperties.Mail mail = props.mail();
        if (mail.host() == null || mail.host().isBlank() || mail.from() == null || mail.from().isBlank()) return false;
        try {
            Properties p = new Properties();
            p.put("mail.smtp.auth", "true");
            p.put("mail.smtp.host", mail.host());
            p.put("mail.smtp.port", String.valueOf(mail.port()));
            p.put("mail.smtp.connectiontimeout", "10000");
            p.put("mail.smtp.timeout", "15000");
            p.put("mail.smtp.starttls.enable", "true");

            Session session = Session.getInstance(p);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(mail.from(), props.app().name()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject, "UTF-8");

            MimeBodyPart text = new MimeBodyPart();
            text.setText(bodyText, "UTF-8");
            MimeMultipart body = new MimeMultipart();
            body.addBodyPart(text);
            message.setContent(body);

            try (Transport transport = session.getTransport("smtp")) {
                transport.connect(mail.host(), mail.port(), mail.username(), mail.password());
                transport.sendMessage(message, message.getAllRecipients());
            }
            return true;
        } catch (Exception e) {
            log.warn("Credential email to {} could not be sent (SMTP host: {}): {}",
                toEmail, mail.host(), e.getClass().getSimpleName());
            return false;
        }
    }
}
