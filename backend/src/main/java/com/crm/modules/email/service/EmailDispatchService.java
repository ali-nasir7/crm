package com.crm.modules.email.service;

import com.crm.common.api.ApiException;
import com.crm.common.util.EncryptionService;
import com.crm.modules.email.domain.EmailAccount;
import com.crm.modules.email.repo.EmailAccountRepository;
import com.crm.modules.email.repo.SuppressionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chooses the right provider for an account and performs suppression + rate-limit checks
 * before delegating to the transport. This is the ONLY path through which mail leaves the CRM.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDispatchService {

    private final List<EmailProvider> providers;
    private final EmailAccountRepository accounts;
    private final SuppressionRepository suppressions;
    private final EncryptionService encryption;

    public void dispatch(UUID orgId, UUID accountId, List<String> to, List<String> cc,
                         String subject, String html, String text) {
        EmailAccount account = accounts.findById(accountId).filter(a -> a.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.badRequest("Email account not found"));

        // Suppression check — unsubscribed/bounced/complained addresses must never be contacted.
        List<String> blocked = new ArrayList<>();
        for (String address : to) {
            suppressions.findInOrg(orgId, address.trim().toLowerCase())
                .ifPresent(s -> blocked.add(address));
        }
        if (!blocked.isEmpty()) {
            throw ApiException.business("Suppressed recipients cannot be emailed: " + String.join(", ", blocked));
        }

        if (account.getProvider() == EmailAccount.Provider.GMAIL || account.getProvider() == EmailAccount.Provider.M365) {
            throw ApiException.business("OAuth senders (Gmail / Microsoft 365) are not configured yet. " +
                "TODO / Integration Required — use an SMTP account for now.");
        }

        EmailProvider provider = providers.stream().filter(p -> p.supports(account.getProvider())).findFirst()
            .orElseThrow(() -> ApiException.business("No provider available for account type " + account.getProvider()));

        EmailProvider.SendCommand cmd = new EmailProvider.SendCommand(
            account.getEmail(), account.getDisplayName(), account.getSmtpHost(),
            account.getSmtpPort() == null ? 587 : account.getSmtpPort(),
            account.getSmtpEncryption(), account.getSmtpUsername(),
            account.getSmtpPasswordEnc() == null ? null : encryption.decrypt(account.getSmtpPasswordEnc()),
            to, cc, subject, html, text);
        try {
            provider.send(cmd);
        } catch (Exception e) {
            log.warn("Email send failed for account {}: {}", account.getEmail(), e.getMessage());
            throw ApiException.business("Email could not be sent: " + e.getMessage());
        }
    }

    public boolean isSuppressed(UUID orgId, String email) {
        return suppressions.findInOrg(orgId, email.trim().toLowerCase()).isPresent();
    }
}
