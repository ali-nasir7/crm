package com.crm.modules.audit.service;

import com.crm.common.api.PageResponse;
import com.crm.modules.audit.domain.AuditLog;
import com.crm.modules.audit.repo.AuditLogRepository;
import com.crm.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository logs;
    private final com.crm.modules.pipeline.repo.PipelineStageRepository stageRepo;
    private final com.crm.modules.identity.repo.UserRepository userRepo;
    private final com.crm.modules.deals.repo.DealRepository dealRepo;
    private final com.crm.modules.pipeline.repo.PipelineRepository pipelineRepo;
    private final com.crm.modules.companies.repo.CompanyRepository companyRepo;
    private final com.crm.modules.clients.repo.ClientRepository clientRepo;

    /**
     * Append an audit entry. Runs in a new transaction so audit history survives business rollbacks.
     * Audit failures are logged but never break the business flow.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, UUID entityId, String entityLabel,
                    Map<String, Object> oldValues, Map<String, Object> newValues) {
        try {
            AuditLog e = new AuditLog();
            var p = CurrentUser.principalOrNull();
            if (p != null) {
                e.setOrganizationId(p.getOrganizationId());
                e.setActorId(p.getId());
                e.setActorEmail(p.getUsername());
            }
            e.setAction(action);
            e.setEntityType(entityType);
            e.setEntityId(entityId);
            e.setEntityLabel(entityLabel);
            e.setOldValues(oldValues);
            e.setNewValues(newValues);
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
                HttpServletRequest req = attrs.getRequest();
                String fwd = req.getHeader("X-Forwarded-For");
                e.setIp(fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : req.getRemoteAddr());
                String ua = req.getHeader("User-Agent");
                e.setUserAgent(ua != null ? ua.substring(0, Math.min(ua.length(), 255)) : null);
            } catch (IllegalStateException ignored) {
                // background worker context — no request bound
            }
            logs.save(e);
        } catch (Exception ex) {
            log.warn("Audit write failed for action {}: {}", action, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(UUID orgId, String action, String entityType, UUID entityId,
                                                  UUID actorId, Instant from, Instant to, int page, int size) {
        Specification<AuditLog> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("organizationId"), orgId));
            if (action != null) ps.add(cb.equal(root.get("action"), action));
            if (entityType != null) ps.add(cb.equal(root.get("entityType"), entityType));
            if (entityId != null) ps.add(cb.equal(root.get("entityId"), entityId));
            if (actorId != null) ps.add(cb.equal(root.get("actorId"), actorId));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<AuditLog> result = logs.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        List<Map<String, Object>> content = result.getContent().stream().<Map<String, Object>>map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("action", e.getAction());
            m.put("entityType", e.getEntityType());
            m.put("entityId", e.getEntityId());
            m.put("entityLabel", e.getEntityLabel());
            m.put("actorEmail", e.getActorEmail());
            m.put("oldValues", e.getOldValues());
            m.put("newValues", e.getNewValues());
            m.put("ip", e.getIp());
            m.put("createdAt", e.getCreatedAt());
            return m;
        }).toList();
        // Human-readable labels for raw ids inside diffs (brief: stage UUID -> "Contacted",
        // user UUID -> email, client UUID -> company name). Resolved at READ time so existing
        // immutable rows are covered too.
        Map<String, String> labels = resolveLabels(orgId, content);
        if (!labels.isEmpty()) {
            for (Map<String, Object> row : content) row.put("labels", labels);
        }
        return PageResponse.of(content, result.getPageable(), result.getTotalElements());
    }

    /** Collects id-valued diff entries across the page and batch-resolves them to names. */
    private Map<String, String> resolveLabels(UUID orgId, List<Map<String, Object>> rows) {
        Set<UUID> stageIds = new HashSet<>(), userIds = new HashSet<>(), clientIds = new HashSet<>(),
            dealIds = new HashSet<>(), pipelineIds = new HashSet<>(), companyIds = new HashSet<>();
        java.util.function.BiConsumer<String, Object> collect = (key, value) -> {
            if (value == null) return;
            UUID id = tryParseUuid(String.valueOf(value));
            if (id == null) return;
            switch (key) {
                case "stageId", "stage" -> stageIds.add(id);
                case "assignedUserId", "userId", "ownerId" -> userIds.add(id);
                case "clientId" -> clientIds.add(id);
                case "dealId" -> dealIds.add(id);
                case "pipelineId" -> pipelineIds.add(id);
                case "companyId" -> companyIds.add(id);
                default -> { }
            }
        };
        for (Map<String, Object> row : rows) {
            collectInto(collect, row.get("oldValues"));
            collectInto(collect, row.get("newValues"));
        }
        Map<String, String> labels = new LinkedHashMap<>();
        stageRepo.findAllById(stageIds).forEach(s -> labels.put(s.getId().toString(), s.getName()));
        userRepo.findAllById(userIds).forEach(u -> labels.put(u.getId().toString(),
            u.getEmail() == null ? u.displayName() : u.getEmail()));
        dealRepo.findAllById(dealIds).forEach(d -> labels.put(d.getId().toString(), d.getTitle()));
        pipelineRepo.findAllById(pipelineIds).forEach(p -> labels.put(p.getId().toString(), p.getName()));
        companyRepo.findAllById(companyIds).forEach(c -> labels.put(c.getId().toString(), c.getName()));
        for (UUID clientId : clientIds) {
            clientRepo.findById(clientId)
                .flatMap(c -> companyRepo.findById(c.getCompanyId()))
                .ifPresent(c -> labels.put(clientId.toString(), c.getName() + " (client)"));
            labels.putIfAbsent(clientId.toString(), "Client " + shortId(clientId));
        }
        return labels;
    }

    @SuppressWarnings("unchecked")
    private void collectInto(java.util.function.BiConsumer<String, Object> collect, Object diff) {
        if (diff instanceof Map<?, ?> m) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) collect.accept(e.getKey(), e.getValue());
        }
    }

    private UUID tryParseUuid(String value) {
        if (value == null || value.length() != 36 || value.charAt(8) != '-') return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException e) { return null; }
    }

    private String shortId(UUID id) { return id == null ? "" : id.toString().substring(0, 8); }
}
