package com.crm.modules.identity.service;

import com.crm.config.CrmProperties;
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

/**
 * Sends onboarding emails for admin-created users. Credentials are NEVER logged and never
 * stored in plaintext. Uses the configured SMTP (env: CRM_MAIL_HOST/PORT/USERNAME/PASSWORD/FROM).
 * With Gmail set CRM_MAIL_HOST=smtp.gmail.com, CRM_MAIL_PORT=587 and an App Password in
 * CRM_MAIL_PASSWORD. If delivery fails the account is still created and the admin gets the
 * temp password in the API response to relay manually - the feature never blocks creation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingMailService {

    private final CrmProperties props;

    public boolean sendOnboarding(String toEmail, String fullName, String tempPassword, String orgName) {
        CrmProperties.Mail mail = props.mail();
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
            message.setSubject("Your " + props.app().name() + " account is ready", "UTF-8");

            String safeName = fullName == null || fullName.isBlank() ? toEmail : fullName;
            MimeBodyPart text = new MimeBodyPart();
            text.setText("Hi " + safeName + ",\n\n"
                + "An account has been created for you on " + props.app().name() + (orgName == null ? "" : " (" + orgName + ")") + ".\n\n"
                + "Login URL: " + props.app().appUrl() + "/login\n"
                + "Email: " + toEmail + "\n"
                + "Temporary password: " + tempPassword + "\n\n"
                + "For security you must choose your own password at first login. "
                + "This temporary password stops working as soon as you set a new one.\n\n"
                + "- " + props.app().name(), "UTF-8");
            MimeMultipart body = new MimeMultipart();
            body.addBodyPart(text);
            message.setContent(body);

            try (Transport transport = session.getTransport("smtp")) {
                transport.connect(mail.host(), mail.port(), mail.username(), mail.password());
                transport.sendMessage(message, message.getAllRecipients());
            }
            return true;
        } catch (Exception e) {
            // Deliberately generic: no credentials, no stack internals with sensitive data.
            log.warn("Onboarding email to {} could not be sent (SMTP host: {}): {}",
                toEmail, mail.host(), e.getClass().getSimpleName());
            return false;
        }
    }
}
