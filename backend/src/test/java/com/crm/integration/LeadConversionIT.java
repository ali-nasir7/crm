package com.crm.integration;

import com.crm.modules.clients.repo.ClientRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.dto.LeadDtos.ConvertLeadRequest;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.leads.service.LeadConversionService;
import com.crm.modules.organization.domain.Organization;
import com.crm.modules.organization.repo.OrganizationRepository;
import com.crm.modules.identity.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Critical test #6: conversion preserves history and does not duplicate data. */
class LeadConversionIT extends IntegrationTestBase {

    @Autowired LeadConversionService conversion;
    @Autowired LeadRepository leads;
    @Autowired ClientRepository clients;
    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired com.crm.modules.companies.repo.CompanyRepository companies;

    @Test
    void conversionCreatesClientReusesCompanyAndKeepsLeadHistory() {
        var admin = users.findByEmailIgnoreCase("admin@test.local").orElseThrow();
        UUID orgId = admin.getOrganizationId();

        Lead lead = new Lead();
        lead.setOrganizationId(orgId);
        lead.setBusinessName("Sunrise Longevity Clinic");
        lead.setWebsite("https://sunrise-clinic.com");
        lead.setFirstName("Ayesha");
        lead.setLastName("Khan");
        lead.setEmail("ayesha@sunrise-clinic.com");
        lead.setStatus(com.crm.modules.leads.domain.LeadStatus.INTERESTED);
        lead = leads.save(lead);
        UUID leadId = lead.getId();

        var result = conversion.convert(orgId, admin.getId(), leadId, new ConvertLeadRequest(null, 5000.0, "USD", null, null));

        // lead preserved + marked converted (history intact)
        Lead after = leads.findById(leadId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(com.crm.modules.leads.domain.LeadStatus.CONVERTED);

        // client exists, linked back to the lead
        var client = clients.findById(result.clientId()).orElseThrow();
        assertThat(client.getConvertedFromLeadId()).isEqualTo(leadId);

        // company was created once and linked to the lead
        assertThat(result.companyId()).isNotNull();
        assertThat(after.getCompanyId()).isEqualTo(result.companyId());

        // converting again is rejected (idempotent)
        try {
            conversion.convert(orgId, admin.getId(), leadId, new ConvertLeadRequest(null, null, null, null, null));
            throw new AssertionError("expected business exception");
        } catch (com.crm.common.api.ApiException e) {
            assertThat(e.getMessage()).contains("already converted");
        }

        // company reused (no duplicates) on a second lead from the same website
        Lead lead2 = new Lead();
        lead2.setOrganizationId(orgId);
        lead2.setBusinessName("Sunrise Longevity Clinic");
        lead2.setWebsite("https://sunrise-clinic.com");
        lead2.setEmail("second@sunrise-clinic.com");
        lead2 = leads.save(lead2);
        var result2 = conversion.convert(orgId, admin.getId(), lead2.getId(), new ConvertLeadRequest(null, null, null, null, null));
        assertThat(result2.companyId()).isEqualTo(result.companyId());
        assertThat(companies.findAll().stream().filter(c -> "sunrise-clinic.com".equals(
            com.crm.common.util.Normalizer.website(c.getWebsite()))).count()).isEqualTo(1);
    }
}
