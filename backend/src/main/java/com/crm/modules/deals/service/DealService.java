package com.crm.modules.deals.service;

import com.crm.common.api.ApiException;
import com.crm.common.api.PageResponse;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.audit.service.AuditService;
import com.crm.modules.deals.domain.Deal;
import com.crm.modules.deals.dto.DealDtos.*;
import com.crm.modules.deals.repo.DealRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.service.LeadAccessPolicy;
import com.crm.modules.notifications.service.NotificationService;
import com.crm.modules.pipeline.repo.PipelineRepository;
import com.crm.modules.pipeline.repo.PipelineStageRepository;
import com.crm.security.UserPrincipal;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DealService {

    private final DealRepository deals;
    private final UserRepository users;
    private final PipelineRepository pipelines;
    private final PipelineStageRepository stages;
    private final LeadAccessPolicy accessPolicy;
    private final ActivityService activities;
    private final AuditService audit;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public PageResponse<DealItem> list(UserPrincipal principal, UUID orgId, String status, UUID ownerId,
                                       UUID stageId, UUID pipelineId, UUID leadId, int page, int size) {
        Set<UUID> visibleUsers = ownerId != null ? Set.of(ownerId)
            : (accessPolicy.scopeOf(principal, orgId) == com.crm.modules.identity.domain.DataScope.ORG ? null : accessPolicy.visibleUserIds(orgId, principal));
        Specification<Deal> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("organizationId"), orgId));
            ps.add(cb.isNull(root.get("deletedAt")));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status.toUpperCase()));
            if (visibleUsers != null) ps.add(root.get("ownerId").in(visibleUsers));
            if (stageId != null) ps.add(cb.equal(root.get("stageId"), stageId));
            if (pipelineId != null) ps.add(cb.equal(root.get("pipelineId"), pipelineId));
            if (leadId != null) ps.add(cb.equal(root.get("leadId"), leadId));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<DealItem> result = deals.findAll(spec, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt")))
            .map(this::toItem);
        return PageResponse.of(result.getContent(), result.getPageable(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DealItem get(UUID orgId, UUID id) {
        return toItem(find(orgId, id));
    }

    @Transactional
    public DealItem create(UUID orgId, UUID actorId, CreateDealRequest req) {
        Deal d = new Deal();
        d.setOrganizationId(orgId);
        d.setTitle(req.title().trim());
        d.setLeadId(req.leadId());
        d.setCompanyId(req.companyId());
        d.setContactId(req.contactId());
        d.setOwnerId(req.ownerId() != null ? req.ownerId() : actorId);
        d.setAmount(req.amount());
        if (req.currency() != null) d.setCurrency(req.currency().toUpperCase());
        d.setProducts(req.products());
        d.setNotes(req.notes());
        if (req.expectedCloseDate() != null) d.setExpectedCloseDate(req.expectedCloseDate());

        if (req.stageId() != null) {
            var stage = stages.findById(req.stageId()).orElseThrow(() -> ApiException.badRequest("Unknown stage"));
            d.setPipelineId(stage.getPipelineId());
            d.setStageId(stage.getId());
            d.setProbability(stage.getProbability());
        } else {
            UUID pipelineId = req.pipelineId() != null ? req.pipelineId()
                : pipelines.findFirstByOrganizationIdAndIsDefaultTrue(orgId).map(p -> p.getId()).orElse(null);
            d.setPipelineId(pipelineId);
            if (pipelineId != null) {
                var first = stages.findByPipelineIdOrderByPositionAsc(pipelineId).stream().findFirst().orElse(null);
                if (first != null) { d.setStageId(first.getId()); d.setProbability(first.getProbability()); }
            }
        }
        if (req.probability() != null) d.setProbability(Math.max(0, Math.min(100, req.probability())));
        deals.save(d);

        activities.record(orgId, ActivityType.OFFER, d.getLeadId(), "Deal created: " + d.getTitle(), null,
            Map.of("amount", String.valueOf(d.getAmount()), "currency", d.getCurrency()), actorId);
        audit.log("DEAL_CREATE", "DEAL", d.getId(), d.getTitle(), null, Map.of("amount", String.valueOf(d.getAmount())));
        return toItem(d);
    }

    @Transactional
    public DealItem update(UUID orgId, UUID id, UpdateDealRequest req) {
        Deal d = find(orgId, id);
        if (req.title() != null) d.setTitle(req.title().trim());
        if (req.amount() != null) d.setAmount(req.amount());
        if (req.currency() != null) d.setCurrency(req.currency().toUpperCase());
        if (req.probability() != null) d.setProbability(Math.max(0, Math.min(100, req.probability())));
        if (req.expectedCloseDate() != null) d.setExpectedCloseDate(req.expectedCloseDate());
        if (req.products() != null) d.setProducts(req.products());
        if (req.notes() != null) d.setNotes(req.notes());
        deals.save(d);
        audit.log("DEAL_UPDATE", "DEAL", d.getId(), d.getTitle(), null, null);
        return toItem(d);
    }

    @Transactional
    public DealItem changeStage(UUID orgId, UUID actorId, UUID id, UUID stageId) {
        Deal d = find(orgId, id);
        var stage = stages.findById(stageId).orElseThrow(() -> ApiException.badRequest("Unknown stage"));
        if (d.getStageId() != null && !stage.getPipelineId().equals(d.getPipelineId())) {
            throw ApiException.badRequest("Stage does not belong to the deal's pipeline");
        }
        d.setStageId(stageId);
        d.setPipelineId(stage.getPipelineId());
        d.setProbability(stage.getProbability());
        if ("WON".equals(stage.getType())) return close(d, "WON", null, actorId);
        if ("LOST".equals(stage.getType())) return close(d, "LOST", "Moved to lost stage", actorId);
        deals.save(d);
        return toItem(d);
    }

    @Transactional
    public DealItem changeStatus(UUID orgId, UUID actorId, UUID id, String status, String lostReason) {
        Deal d = find(orgId, id);
        return close(d, status.toUpperCase(), lostReason, actorId);
    }

    private DealItem close(Deal d, String status, String lostReason, UUID actorId) {
        if (!Set.of("OPEN", "WON", "LOST").contains(status)) throw ApiException.badRequest("Invalid deal status");
        UUID orgId = d.getOrganizationId();
        Map<String, Object> before = Map.of("status", d.getStatus());
        d.setStatus(status);
        d.setLostReason("LOST".equals(status) ? lostReason : null);
        d.setClosedAt("OPEN".equals(status) ? null : Instant.now());
        if ("OPEN".equals(status)) d.setLostReason(null);
        deals.save(d);

        activities.record(orgId, "WON".equals(status) ? ActivityType.SYSTEM : ActivityType.SYSTEM, d.getLeadId(),
            "Deal " + status.toLowerCase() + ": " + d.getTitle(), lostReason, null, actorId);
        audit.log("DEAL_STATUS_CHANGE", "DEAL", d.getId(), d.getTitle(), before, Map.of("status", status));
        if (d.getOwnerId() != null && !d.getOwnerId().equals(actorId)) {
            notifications.notify(orgId, d.getOwnerId(), "DEAL_" + status, "Deal " + status.toLowerCase() + ": " + d.getTitle(),
                null, "DEAL", d.getId());
        }
        return toItem(d);
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Deal d = find(orgId, id);
        d.setDeletedAt(Instant.now());
        deals.save(d);
        audit.log("DEAL_DELETE", "DEAL", d.getId(), d.getTitle(), null, null);
    }

    @Transactional(readOnly = true)
    public DealSummary summary(UUID orgId) {
        long open = deals.countByOrganizationIdAndStatus(orgId, "OPEN");
        long won = deals.countByOrganizationIdAndStatus(orgId, "WON");
        long lost = deals.countByOrganizationIdAndStatus(orgId, "LOST");
        BigDecimal openValue = deals.sumOpenAmount(orgId);
        BigDecimal weighted = deals.sumWeightedAmount(orgId);
        return new DealSummary(openValue, weighted.setScale(2, RoundingMode.HALF_UP), deals.sumAmountByStatus(orgId, "WON"),
            deals.sumAmountByStatus(orgId, "LOST"), open, won, lost, weighted);
    }

    private Deal find(UUID orgId, UUID id) {
        return deals.findInOrg(orgId, id).filter(d -> d.getDeletedAt() == null)
            .orElseThrow(() -> ApiException.notFound("Deal not found"));
    }

    public DealItem toItem(Deal d) {
        String stageName = d.getStageId() != null ? stages.findById(d.getStageId()).map(s -> s.getName()).orElse(null) : null;
        return new DealItem(d.getId(), d.getTitle(), d.getLeadId(), null, d.getCompanyId(), null, d.getContactId(),
            d.getOwnerId(), users.findById(d.getOwnerId()).map(u -> u.displayName()).orElse(null),
            d.getPipelineId(), d.getStageId(), stageName, d.getAmount(), d.getCurrency(), d.getProbability(),
            d.getExpectedCloseDate(), d.getClosedAt(), d.getStatus(), d.getLostReason(), d.getProducts(),
            d.getNotes(), d.getClientId(), d.getCreatedAt());
    }
}
