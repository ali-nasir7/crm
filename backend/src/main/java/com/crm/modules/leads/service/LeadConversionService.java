package com.crm.modules.leads.service;

import com.crm.common.api.ApiException;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.audit.service.AuditService;
import com.crm.modules.clients.domain.Client;
import com.crm.modules.clients.repo.ClientRepository;
import com.crm.modules.companies.domain.Company;
import com.crm.modules.companies.repo.CompanyRepository;
import com.crm.modules.contacts.domain.Contact;
import com.crm.modules.contacts.repo.ContactRepository;
import com.crm.modules.deals.domain.Deal;
import com.crm.modules.deals.repo.DealRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.dto.LeadDtos.ConvertLeadRequest;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.pipeline.repo.PipelineStageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Lead → Company + Contact + Client + Deal conversion. Reuses existing company/contact records when
 * they already match (no duplicate data), preserves the complete lead history by keeping the lead
 * (status CONVERTED) and linking client.convertedFromLeadId.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadConversionService {

    private final LeadRepository leads;
    private final CompanyRepository companies;
    private final ContactRepository contacts;
    private final ClientRepository clients;
    private final DealRepository deals;
    private final PipelineStageRepository stages;
    private final ActivityService activities;
    private final AuditService audit;

    public record ConversionResult(UUID clientId, UUID companyId, UUID contactId, UUID dealId) {}

    @Transactional
    public ConversionResult convert(UUID orgId, UUID actorId, UUID leadId, ConvertLeadRequest req) {
        Lead lead = leads.findInOrg(orgId, leadId).orElseThrow(() -> ApiException.notFound("Lead not found"));
        if (lead.getStatus() == com.crm.modules.leads.domain.LeadStatus.CONVERTED) {
            throw ApiException.business("Lead is already converted");
        }

        // 1. Company — reuse by website or exact name
        Company company = findOrCreateCompany(orgId, actorId, lead);

        // 2. Contact — reuse by email when present
        Contact contact = findOrCreateContact(orgId, actorId, lead, company);

        // 3. Client
        Client client = clients.findByCompany(orgId, company.getId()).orElseGet(() -> {
            Client c = new Client();
            c.setOrganizationId(orgId);
            c.setCompanyId(company.getId());
            c.setConvertedAt(Instant.now());
            return c;
        });
        client.setPrimaryContactId(contact.getId());
        client.setAccountManagerId(lead.getAssignedUserId());
        client.setConvertedFromLeadId(lead.getId());
        client.setConvertedAt(Instant.now());
        if (req.clientStatus() != null) client.setStatus(req.clientStatus().toUpperCase());
        if (req.notes() != null) client.setNotes(req.notes());
        clients.save(client);

        // 4. Deal (open opportunity) when value is provided
        UUID dealId = null;
        if (req.amount() != null || req.dealStageId() != null) {
            Deal deal = new Deal();
            deal.setOrganizationId(orgId);
            deal.setTitle(lead.getBusinessName() + " — engagement");
            deal.setLeadId(lead.getId());
            deal.setCompanyId(company.getId());
            deal.setContactId(contact.getId());
            deal.setOwnerId(lead.getAssignedUserId() != null ? lead.getAssignedUserId() : actorId);
            deal.setAmount(req.amount() == null ? null : BigDecimal.valueOf(req.amount()));
            deal.setCurrency(req.currency() == null ? "USD" : req.currency().toUpperCase());
            if (req.dealStageId() != null) {
                var stage = stages.findById(req.dealStageId()).orElseThrow(() -> ApiException.badRequest("Unknown stage"));
                deal.setPipelineId(stage.getPipelineId());
                deal.setStageId(stage.getId());
                deal.setProbability(stage.getProbability());
            }
            deal.setClientId(client.getId());
            deals.save(deal);
            dealId = deal.getId();
        }

        // 5. Lead lifecycle
        lead.setStatus(com.crm.modules.leads.domain.LeadStatus.CONVERTED);
        lead.setCompanyId(company.getId());
        lead.setContactId(contact.getId());
        leads.save(lead);

        activities.record(orgId, ActivityType.CONVERSION, lead.getId(), "Lead converted to client", null,
            Map.of("clientId", client.getId().toString(), "companyId", company.getId().toString()), actorId);
        audit.log("LEAD_CONVERT", "LEAD", lead.getId(), lead.getBusinessName(), null,
            Map.of("clientId", client.getId().toString()));
        return new ConversionResult(client.getId(), company.getId(), contact.getId(), dealId);
    }

    private Company findOrCreateCompany(UUID orgId, UUID actorId, Lead lead) {
        if (lead.getCompanyId() != null) {
            return companies.findById(lead.getCompanyId()).orElse(null);
        }
        if (lead.getWebsite() != null && !lead.getWebsite().isBlank()) {
            var bySite = companies.findByWebsite(orgId, com.crm.common.util.Normalizer.website(lead.getWebsite()));
            if (!bySite.isEmpty()) return bySite.get(0);
        }
        var byName = companies.findByNameExact(orgId, lead.getBusinessName());
        if (!byName.isEmpty()) return byName.get(0);

        Company c = new Company();
        c.setOrganizationId(orgId);
        c.setName(lead.getBusinessName());
        c.setWebsite(lead.getWebsite());
        c.setIndustry(lead.getIndustry());
        c.setPhone(lead.getPhone());
        c.setEmail(lead.getEmail());
        c.setCountry(lead.getCountry());
        c.setState(lead.getState());
        c.setCity(lead.getCity());
        c.setAddress(lead.getAddress());
        c.setLinkedin(lead.getLinkedin());
        c.setCompanySize(lead.getCompanySize());
        c.setOwnerId(lead.getAssignedUserId());
        c.setCreatedBy(actorId);
        return companies.save(c);
    }

    private Contact findOrCreateContact(UUID orgId, UUID actorId, Lead lead, Company company) {
        if (lead.getContactId() != null) {
            Contact existing = contacts.findById(lead.getContactId()).orElse(null);
            if (existing != null) return existing;
        }
        if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
            var byEmail = contacts.findByEmail(orgId, lead.getEmail());
            if (!byEmail.isEmpty()) return byEmail.get(0);
        }
        Contact c = new Contact();
        c.setOrganizationId(orgId);
        c.setCompanyId(company != null ? company.getId() : null);
        c.setFirstName(lead.getFirstName() == null || lead.getFirstName().isBlank() ? "Contact" : lead.getFirstName());
        c.setLastName(lead.getLastName() == null || lead.getLastName().isBlank() ? company != null ? company.getName() : "Unknown" : lead.getLastName());
        c.setJobTitle(lead.getJobTitle());
        c.setEmail(lead.getEmail());
        c.setSecondaryEmail(lead.getSecondaryEmail());
        c.setPhone(lead.getPhone());
        c.setWhatsapp(lead.getWhatsapp());
        c.setLinkedin(lead.getLinkedin());
        c.setOwnerId(lead.getAssignedUserId());
        c.setPrimary(true);
        c.setCreatedBy(actorId);
        return contacts.save(c);
    }
}
