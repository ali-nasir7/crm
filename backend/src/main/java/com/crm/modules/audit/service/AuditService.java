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
        return PageResponse.of(content, result.getPageable(), result.getTotalElements());
    }
}
