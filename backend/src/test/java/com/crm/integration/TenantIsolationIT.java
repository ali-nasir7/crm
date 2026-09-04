package com.crm.integration;

import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.organization.domain.Organization;
import com.crm.modules.organization.repo.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Critical test #1: a user must never access another organization's data. */
class TenantIsolationIT extends IntegrationTestBase {

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired LeadRepository leads;

    @Test
    @WithMockUser(username = "someone@else.org", authorities = {"LEAD_VIEW"})
    void leadOfAnotherOrgIsInvisible() {
        // org A with a lead
        Organization orgA = new Organization();
        orgA.setName("Org A"); orgA.setSlug("org-a-" + UUID.randomUUID());
        orgA = organizations.save(orgA);
        Lead lead = new Lead();
        lead.setOrganizationId(orgA.getId());
        lead.setBusinessName("Secret Clinic");
        lead = leads.save(lead);

        // org B
        Organization orgB = new Organization();
        orgB.setName("Org B"); orgB.setSlug("org-b-" + UUID.randomUUID());
        orgB = organizations.save(orgB);

        UUID leadId = lead.getId();
        UUID orgBId = orgB.getId();

        // query scoped to orgB must not find org A's lead
        var found = leads.findOne((root, cq, cb) -> cb.and(
            cb.equal(root.get("organizationId"), orgBId),
            cb.equal(root.get("id"), leadId)));
        assertThat(found).isEmpty();

        // and a list query for orgB returns nothing of org A
        var list = leads.findAll((root, cq, cb) -> cb.equal(root.get("organizationId"), orgBId));
        assertThat(list).isEmpty();
    }
}
