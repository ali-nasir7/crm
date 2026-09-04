package com.crm.modules.leads.service;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.*;

/** Composable, injection-safe lead filters. Mirrors the saved-view JSON schema. */
public final class LeadSpecs {
    private LeadSpecs() {}

    public record LeadFilters(
        String q, String status, UUID pipelineId, UUID stageId, UUID assignedTo, UUID teamId, UUID sourceId,
        String country, String city, String state, String industry, String companySize,
        List<String> tags, Integer minScore, Integer maxScore,
        Boolean uncontacted, Boolean hasEmail, Boolean hasPhone,
        Instant lastContactedBefore, Instant lastContactedAfter,
        Instant nextFollowUpBefore, Instant nextFollowUpAfter,
        Instant createdFrom, Instant createdTo,
        Map<String, Object> custom) {}

    public static Specification<com.crm.modules.leads.domain.Lead> from(LeadFilters f) {
        Specification<com.crm.modules.leads.domain.Lead> spec = (root, cq, cb) -> cb.conjunction();
        if (f == null) return spec;

        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + f.q().trim().toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.or(
                cb.like(cb.lower(root.get("businessName")), like),
                cb.like(cb.lower(root.get("firstName")), like),
                cb.like(cb.lower(root.get("lastName")), like),
                cb.like(cb.lower(root.get("email")), like),
                cb.like(cb.lower(root.get("phone")), like),
                cb.like(cb.lower(root.get("website")), like),
                cb.like(cb.lower(root.get("linkedin")), like),
                cb.like(cb.lower(root.get("city")), like)));
        }
        if (f.status() != null && !f.status().isBlank()) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("status"), com.crm.modules.leads.domain.LeadStatus.valueOf(f.status())));
        }
        if (f.pipelineId() != null) spec = spec.and(eq("pipelineId", f.pipelineId()));
        if (f.stageId() != null) spec = spec.and(eq("stageId", f.stageId()));
        if (f.assignedTo() != null) spec = spec.and(eq("assignedUserId", f.assignedTo()));
        if (f.teamId() != null) {
            spec = spec.and((root, cq, cb) -> {
                var sub = cq.subquery(UUID.class);
                var memberRoot = sub.from(com.crm.modules.identity.domain.User.class);
                sub.select(memberRoot.get("id")).where(memberRoot.join("teams").get("id").in(Set.of(f.teamId())));
                return root.get("assignedUserId").in(sub);
            });
        }
        if (f.sourceId() != null) spec = spec.and(eq("sourceId", f.sourceId()));
        if (f.country() != null && !f.country().isBlank()) spec = spec.and(iEq("country", f.country()));
        if (f.city() != null && !f.city().isBlank()) spec = spec.and(iEq("city", f.city()));
        if (f.state() != null && !f.state().isBlank()) spec = spec.and(iEq("state", f.state()));
        if (f.industry() != null && !f.industry().isBlank()) spec = spec.and(iEq("industry", f.industry()));
        if (f.companySize() != null && !f.companySize().isBlank()) spec = spec.and(iEq("companySize", f.companySize()));
        if (f.tags() != null && !f.tags().isEmpty()) {
            spec = spec.and((root, cq, cb) -> root.join("tags").get("name").in(f.tags()));
        }
        if (f.minScore() != null) spec = spec.and((root, cq, cb) -> cb.ge(root.get("score"), f.minScore()));
        if (f.maxScore() != null) spec = spec.and((root, cq, cb) -> cb.le(root.get("score"), f.maxScore()));
        if (Boolean.TRUE.equals(f.uncontacted())) {
            spec = spec.and((root, cq, cb) -> cb.isNull(root.get("lastContactedAt")));
        }
        if (Boolean.TRUE.equals(f.hasEmail())) spec = spec.and((root, cq, cb) -> cb.isNotNull(root.get("email")));
        if (Boolean.TRUE.equals(f.hasPhone())) spec = spec.and((root, cq, cb) -> cb.isNotNull(root.get("phone")));
        if (f.lastContactedBefore() != null) spec = spec.and((root, cq, cb) -> cb.lessThan(root.get("lastContactedAt"), f.lastContactedBefore()));
        if (f.lastContactedAfter() != null) spec = spec.and((root, cq, cb) -> cb.greaterThan(root.get("lastContactedAt"), f.lastContactedAfter()));
        if (f.nextFollowUpBefore() != null) spec = spec.and((root, cq, cb) -> cb.lessThanOrEqualTo(root.get("nextFollowUpAt"), f.nextFollowUpBefore()));
        if (f.nextFollowUpAfter() != null) spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("nextFollowUpAt"), f.nextFollowUpAfter()));
        if (f.createdFrom() != null) spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), f.createdFrom()));
        if (f.createdTo() != null) spec = spec.and((root, cq, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), f.createdTo()));
        if (f.custom() != null) {
            for (Map.Entry<String, Object> e : f.custom().entrySet()) {
                String key = e.getKey();
                Object value = e.getValue();
                spec = spec.and((root, cq, cb) -> cb.equal(
                    cb.function("jsonb_extract_path_text", String.class, root.get("customFields"), cb.literal(key)),
                    String.valueOf(value)));
            }
        }
        return spec;
    }

    private static Specification<com.crm.modules.leads.domain.Lead> eq(String field, Object value) {
        return (root, cq, cb) -> cb.equal(root.get(field), value);
    }

    private static Specification<com.crm.modules.leads.domain.Lead> iEq(String field, String value) {
        return (root, cq, cb) -> cb.equal(cb.lower(cb.trim(root.get(field))), value.trim().toLowerCase());
    }
}
