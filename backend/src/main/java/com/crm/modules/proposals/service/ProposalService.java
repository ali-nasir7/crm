package com.crm.modules.proposals.service;

import com.crm.common.api.ApiException;
import com.crm.common.api.PageResponse;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.audit.service.AuditService;
import com.crm.modules.companies.repo.CompanyRepository;
import com.crm.modules.contacts.repo.ContactRepository;
import com.crm.modules.counters.service.CounterService;
import com.crm.modules.leads.service.LeadAccessPolicy;
import com.crm.modules.proposals.domain.Proposal;
import com.crm.modules.proposals.domain.ProposalItem;
import com.crm.modules.proposals.dto.ProposalDtos.*;
import com.crm.modules.proposals.repo.ProposalItemRepository;
import com.crm.modules.proposals.repo.ProposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProposalService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "SENT", "VIEWED", "ACCEPTED", "REJECTED", "EXPIRED");

    private final ProposalRepository proposals;
    private final ProposalItemRepository items;
    private final LeadAccessPolicy accessPolicy;
    private final CompanyRepository companies;
    private final ContactRepository contacts;
    private final CounterService counters;
    private final ActivityService activities;
    private final AuditService audit;
    private final ProposalPdfRenderer pdfRenderer;

    @Transactional(readOnly = true)
    public PageResponse<ProposalItem_> list(UUID orgId, UUID leadId, String status, int page, int size) {
        var result = proposals.findAll((root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("organizationId"), orgId));
            if (leadId != null) ps.add(cb.equal(root.get("leadId"), leadId));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status.toUpperCase()));
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, PageRequest.of(page, Math.min(size, 100)));
        List<ProposalItem_> content = result.getContent().stream().map(this::toItem).toList();
        return PageResponse.of(content, result.getPageable(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProposalItem_ get(UUID orgId, UUID id) {
        return toItem(find(orgId, id));
    }

    @Transactional
    public ProposalItem_ create(UUID orgId, UUID actorId, CreateProposalRequest req) {
        if (req.leadId() != null) accessPolicy.assertCanAccess(orgId, req.leadId());
        Proposal p = new Proposal();
        p.setOrganizationId(orgId);
        p.setTitle(req.title().trim());
        p.setDescription(req.description());
        p.setLeadId(req.leadId());
        p.setDealId(req.dealId());
        p.setCompanyId(req.companyId());
        p.setContactId(req.contactId());
        p.setCurrency(req.currency() == null ? "USD" : req.currency().toUpperCase());
        p.setDiscountPercent(req.discountPercent());
        p.setTaxPercent(req.taxPercent());
        p.setValidUntil(req.validUntil());
        p.setTerms(req.terms());
        p.setProposalNumber("PRP-" + counters.next(orgId, "proposal"));
        proposals.save(p);

        if (req.items() != null) {
            int pos = 0;
            for (ItemRequest ir : req.items()) {
                ProposalItem item = new ProposalItem();
                item.setProposalId(p.getId());
                item.setName(ir.name().trim());
                item.setDescription(ir.description());
                item.setQuantity(ir.quantity() == null ? BigDecimal.ONE : ir.quantity());
                item.setUnitPrice(ir.unitPrice() == null ? BigDecimal.ZERO : ir.unitPrice());
                item.setPosition(pos++);
                items.save(item);
            }
        }

        activities.record(orgId, ActivityType.PROPOSAL, req.leadId(), "Proposal created: " + p.getTitle(), null,
            Map.of("proposalNumber", p.getProposalNumber()), actorId);
        audit.log("PROPOSAL_CREATE", "PROPOSAL", p.getId(), p.getProposalNumber(), null, null);
        return toItem(p);
    }

    @Transactional
    public ProposalItem_ update(UUID orgId, UUID id, UpdateProposalRequest req) {
        Proposal p = find(orgId, id);
        if (!"DRAFT".equals(p.getStatus())) throw ApiException.business("Only draft proposals can be edited");
        if (req.title() != null) p.setTitle(req.title().trim());
        if (req.description() != null) p.setDescription(req.description());
        if (req.currency() != null) p.setCurrency(req.currency().toUpperCase());
        if (req.discountPercent() != null) p.setDiscountPercent(req.discountPercent());
        if (req.taxPercent() != null) p.setTaxPercent(req.taxPercent());
        if (req.validUntil() != null) p.setValidUntil(req.validUntil());
        if (req.terms() != null) p.setTerms(req.terms());
        return toItem(p);
    }

    @Transactional
    public ProposalItem_ addItem(UUID orgId, UUID id, ItemRequest req) {
        Proposal p = find(orgId, id);
        if (!"DRAFT".equals(p.getStatus())) throw ApiException.business("Only draft proposals can be modified");
        ProposalItem item = new ProposalItem();
        item.setProposalId(p.getId());
        item.setName(req.name().trim());
        item.setDescription(req.description());
        item.setQuantity(req.quantity() == null ? BigDecimal.ONE : req.quantity());
        item.setUnitPrice(req.unitPrice() == null ? BigDecimal.ZERO : req.unitPrice());
        item.setPosition(items.findByProposalIdOrderByPositionAsc(p.getId()).size());
        items.save(item);
        return toItem(p);
    }

    @Transactional
    public void removeItem(UUID orgId, UUID id, UUID itemId) {
        Proposal p = find(orgId, id);
        if (!"DRAFT".equals(p.getStatus())) throw ApiException.business("Only draft proposals can be modified");
        items.findById(itemId).filter(i -> i.getProposalId().equals(p.getId())).ifPresent(items::delete);
    }

    @Transactional
    public ProposalItem_ changeStatus(UUID orgId, UUID actorId, UUID id, String status) {
        String s = status.toUpperCase();
        if (!STATUSES.contains(s)) throw ApiException.badRequest("Invalid proposal status");
        Proposal p = find(orgId, id);
        String before = p.getStatus();
        p.setStatus(s);
        if ("SENT".equals(s) && p.getSentAt() == null) p.setSentAt(Instant.now());
        if ("VIEWED".equals(s) && p.getViewedAt() == null) p.setViewedAt(Instant.now());
        if ("ACCEPTED".equals(s) || "REJECTED".equals(s)) p.setDecidedAt(Instant.now());
        activities.record(orgId, ActivityType.PROPOSAL, p.getLeadId(), "Proposal " + s.toLowerCase() + ": " + p.getTitle(), null,
            Map.of("from", before, "to", s), actorId);
        audit.log("PROPOSAL_STATUS_CHANGE", "PROPOSAL", p.getId(), p.getProposalNumber(), Map.of("status", before), Map.of("status", s));
        return toItem(p);
    }

    /** Marks as sent. Actual email delivery is done by the email module (proposal send integration). */
    @Transactional
    public ProposalItem_ markSent(UUID orgId, UUID actorId, UUID id) {
        return changeStatus(orgId, actorId, id, "SENT");
    }

    public byte[] renderPdf(UUID orgId, UUID id) {
        Proposal p = find(orgId, id);
        return pdfRenderer.render(p, items.findByProposalIdOrderByPositionAsc(p.getId()),
            p.getCompanyId() != null ? companies.findById(p.getCompanyId()).orElse(null) : null,
            p.getContactId() != null ? contacts.findById(p.getContactId()).orElse(null) : null);
    }

    private Proposal find(UUID orgId, UUID id) {
        return proposals.findById(id).filter(p -> p.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Proposal not found"));
    }

    public ProposalItem_ toItem(Proposal p) {
        List<ProposalItem> items = this.items.findByProposalIdOrderByPositionAsc(p.getId());
        var t = ProposalTotals.of(p, items);
        var company = p.getCompanyId() != null ? companies.findById(p.getCompanyId()).orElse(null) : null;
        var contact = p.getContactId() != null ? contacts.findById(p.getContactId()).orElse(null) : null;
        return new ProposalItem_(p.getId(), p.getProposalNumber(), p.getTitle(), p.getDescription(), p.getStatus(),
            p.getLeadId(), null, p.getDealId(), p.getCompanyId(), company != null ? company.getName() : null,
            p.getContactId(), contact != null ? contact.displayName() : null, p.getCurrency(),
            t.subtotal(), p.getDiscountPercent(), t.discountAmount(), t.taxPercent(), t.taxAmount(), t.total(),
            p.getValidUntil(), p.getTerms(), p.getSentAt(), p.getViewedAt(), p.getDecidedAt(),
            items.stream().map(i -> new ItemItem(i.getId(), i.getName(), i.getDescription(), i.getQuantity(), i.getUnitPrice(),
                i.getUnitPrice().multiply(i.getQuantity()).setScale(2, RoundingMode.HALF_UP))).toList(),
            p.getCreatedAt());
    }
}
