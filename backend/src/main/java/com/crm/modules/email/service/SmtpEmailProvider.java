package com.crm.modules.email.service;

import com.crm.modules.email.domain.EmailAccount;
import jakarta.mail.Message;
import jakarta.mail.Transport;
import jakarta.mail.internet.*;
import org.springframework.stereotype.Component;

import java.util.Properties;

import jakarta.mail.Session;
import java.util.List;

@Component
public class SmtpEmailProvider implements EmailProvider {

    @Override
    public boolean supports(EmailAccount.Provider provider) {
        return provider == EmailAccount.Provider.SMTP;
    }

    @Override
    public String send(SendCommand cmd) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", cmd.host());
        props.put("mail.smtp.port", String.valueOf(cmd.port()));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "20000");
        switch (cmd.encryption() == null ? "STARTTLS" : cmd.encryption().toUpperCase()) {
            case "SSL", "SSL_TLS" -> props.put("mail.smtp.ssl.enable", "true");
            case "NONE" -> props.put("mail.smtp.auth", cmd.password() == null || cmd.password().isBlank() ? "false" : "true");
            default -> props.put("mail.smtp.starttls.enable", "true");
        }

        Session session = Session.getInstance(props);
        MimeMessage message = new MimeMessage(session);
        InternetAddress from = cmd.fromName() != null && !cmd.fromName().isBlank()
            ? new InternetAddress(cmd.fromEmail(), cmd.fromName())
            : new InternetAddress(cmd.fromEmail());
        message.setFrom(from);
        message.setRecipients(Message.RecipientType.TO, toAddresses(cmd.to()));
        if (cmd.cc() != null && !cmd.cc().isEmpty()) {
            message.setRecipients(Message.RecipientType.CC, toAddresses(cmd.cc()));
        }
        message.setSubject(cmd.subject() == null ? "" : cmd.subject(), "UTF-8");

        MimeMultipart multipart = new MimeMultipart("alternative");
        if (cmd.text() != null && !cmd.text().isBlank()) {
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(cmd.text(), "UTF-8");
            multipart.addBodyPart(textPart);
        }
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(cmd.html() == null ? "" : cmd.html(), "text/html; charset=utf-8");
        multipart.addBodyPart(htmlPart);
        message.setContent(multipart);

        try (Transport transport = session.getTransport("smtp")) {
            transport.connect(cmd.host(), cmd.port(), cmd.username(), cmd.password());
            transport.sendMessage(message, message.getAllRecipients());
        }
        return null; // SMTP servers rarely return a message id
    }

    private InternetAddress[] toAddresses(List<String> emails) throws AddressException {
        InternetAddress[] out = new InternetAddress[emails.size()];
        for (int i = 0; i < emails.size(); i++) out[i] = new InternetAddress(emails.get(i));
        return out;
    }
}
