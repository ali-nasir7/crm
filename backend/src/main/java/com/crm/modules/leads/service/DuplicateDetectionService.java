package com.crm.modules.leads.service;

import com.crm.common.api.ApiException;
import com.crm.common.util.Normalizer;
import com.crm.modules.leads.repo.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Duplicate detection with configurable rules (org settings → duplicateRules).
 * Matching is deterministic + normalized: email (lower), phone (last 10 digits),
 * website (strip scheme/www), linkedin (strip query), company name (normalized).
 */
@Service
@RequiredArgsConstructor
public class DuplicateDetectionService {

    public record DuplicateMatch(String field, UUID existingLeadId, String existingLabel) {}

    private final LeadRepository leads;
    private final com.crm.modules.organization.service.SettingsService settings;

    public Map<String, Object> rules(UUID orgId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> dup = (Map<String, Object>) settings.get(orgId).get("duplicateRules");
        return dup;
    }

    public boolean ruleEnabled(UUID orgId, String rule) {
        Object v = rules(orgId).get(rule);
        return v == null || Boolean.TRUE.equals(v); // default: enabled
    }

    /** First duplicate found for the given candidate channels (skipping excludeLeadId). */
    public DuplicateMatch findDuplicate(UUID orgId, String email, String phone, String website, String linkedin,
                                        String businessName, UUID excludeLeadId) {
        if (email != null && ruleEnabled(orgId, "email")) {
            var hit = leads.findOne((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.equal(cb.lower(root.get("email")), Normalizer.email(email))));
            if (hit.isPresent() && !hit.get().getId().equals(excludeLeadId)) {
                return new DuplicateMatch("email", hit.get().getId(), hit.get().getBusinessName());
            }
        }
        if (phone != null && ruleEnabled(orgId, "phone")) {
            String p = Normalizer.phone(phone);
            if (p != null && p.length() >= 7) {
                var hit = leads.findOne((root, cq, cb) -> cb.and(
                    cb.equal(root.get("organizationId"), orgId),
                    cb.equal(cb.lower(root.get("phone")), p)));
                if (hit.isPresent() && !hit.get().getId().equals(excludeLeadId)) {
                    return new DuplicateMatch("phone", hit.get().getId(), hit.get().getBusinessName());
                }
            }
        }
        if (website != null && ruleEnabled(orgId, "website")) {
            String w = Normalizer.website(website);
            if (w != null) {
                var hit = leads.findOne((root, cq, cb) -> cb.and(
                    cb.equal(root.get("organizationId"), orgId),
                    cb.equal(cb.lower(root.get("website")), w)));
                if (hit.isPresent() && !hit.get().getId().equals(excludeLeadId)) {
                    return new DuplicateMatch("website", hit.get().getId(), hit.get().getBusinessName());
                }
            }
        }
        if (linkedin != null && ruleEnabled(orgId, "linkedin")) {
            String l = Normalizer.linkedin(linkedin);
            if (l != null) {
                var hit = leads.findOne((root, cq, cb) -> cb.and(
                    cb.equal(root.get("organizationId"), orgId),
                    cb.equal(cb.lower(root.get("linkedin")), l)));
                if (hit.isPresent() && !hit.get().getId().equals(excludeLeadId)) {
                    return new DuplicateMatch("linkedin", hit.get().getId(), hit.get().getBusinessName());
                }
            }
        }
        if (businessName != null && ruleEnabled(orgId, "companyName")) {
            String n = Normalizer.name(businessName);
            var hit = leads.findOne((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.equal(cb.lower(root.get("businessName")), n)));
            if (hit.isPresent() && !hit.get().getId().equals(excludeLeadId)) {
                return new DuplicateMatch("companyName", hit.get().getId(), hit.get().getBusinessName());
            }
        }
        return null;
    }

    /** Interactive create path: reject duplicates outright (the API returns 409 with details). */
    public void assertNoDuplicate(UUID orgId, String email, String phone, String website, String linkedin, String businessName) {
        DuplicateMatch match = findDuplicate(orgId, email, phone, website, linkedin, businessName, null);
        if (match != null) {
            throw ApiException.conflict("Duplicate lead detected on " + match.field() + ": " + match.existingLabel());
        }
    }
}
