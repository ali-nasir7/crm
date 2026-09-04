package com.crm.modules.email.service;

import java.util.List;

/**
 * Sending abstraction — the CRM never talks to a specific vendor directly.
 * Implementations: {@link SmtpEmailProvider} (works out of the box), and Gmail / Microsoft Graph
 * OAuth senders (TODO / Integration Required — need registered OAuth applications).
 */
public interface EmailProvider {

    boolean supports(com.crm.modules.email.domain.EmailAccount.Provider provider);

    /** Sends a message; returns a provider message id when available. */
    String send(SendCommand command) throws Exception;

    record SendCommand(String fromEmail, String fromName, String host, int port, String encryption,
                       String username, String password,
                       List<String> to, List<String> cc, String subject, String html, String text) {}
}
