package com.crm.integration;

import com.crm.modules.email.domain.Suppression;
import com.crm.modules.email.service.SuppressionService;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.repo.LeadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Critical test #7: suppressed contacts can never be emailed (checked at dispatch layer). */
class CampaignSuppressionIT extends IntegrationTestBase {

    @Autowired SuppressionService suppressions;
    @Autowired com.crm.modules.email.service.EmailDispatchService dispatch;
    @Autowired UserRepository users;
    @Autowired LeadRepository leads;

    @Test
    void suppressedEmailIsRejectedBeforeAnyProviderCall() {
        var admin = users.findByEmailIgnoreCase("admin@test.local").orElseThrow();
        UUID orgId = admin.getOrganizationId();
        suppressions.add(orgId, "optout@clinic.com", Suppression.Reason.UNSUBSCRIBE, "clicked unsubscribe");

        assertThat(dispatch.isSuppressed(orgId, "optout@clinic.com")).isTrue();
        assertThat(dispatch.isSuppressed(orgId, "other@clinic.com")).isFalse();

        // dispatch with a suppressed recipient must throw a business exception before touching SMTP
        try {
            dispatch.dispatch(orgId, UUID.randomUUID(), java.util.List.of("optout@clinic.com"), null, "s", "<p>h</p>", "t");
            throw new AssertionError("expected business exception");
        } catch (com.crm.common.api.ApiException e) {
            assertThat(e.getMessage()).contains("Suppressed");
        }
    }
}
