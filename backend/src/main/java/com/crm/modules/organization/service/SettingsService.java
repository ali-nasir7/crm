package com.crm.modules.organization.service;

import com.crm.common.api.ApiException;
import com.crm.modules.organization.domain.Organization;
import com.crm.modules.organization.repo.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Organization settings (duplicate rules, sending window, targets) with schema defaults. */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final OrganizationRepository organizations;

    public Map<String, Object> defaults() {
        Map<String, Object> dup = new LinkedHashMap<>();
        dup.put("email", true); dup.put("phone", true); dup.put("website", true);
        dup.put("linkedin", false); dup.put("companyName", false);
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("startHour", 8); window.put("endHour", 18); window.put("timezone", "UTC");
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("duplicateRules", dup);
        s.put("sendingWindow", window);
        s.put("dailySendLimitPerAccount", 200);
        s.put("emailFooterUnsubscribe", true);
        return s;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID orgId) {
        Map<String, Object> merged = defaults();
        Organization org = organizations.findById(orgId).orElseThrow(() -> ApiException.notFound("Organization not found"));
        if (org.getSettings() != null) deepMerge(merged, org.getSettings());
        return merged;
    }

    @Transactional
    public Map<String, Object> update(UUID orgId, Map<String, Object> patch) {
        Organization org = organizations.findById(orgId).orElseThrow(() -> ApiException.notFound("Organization not found"));
        Map<String, Object> current = org.getSettings() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(org.getSettings());
        deepMerge(current, patch);
        org.setSettings(current);
        return get(orgId);
    }

    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> base, Map<String, Object> patch) {
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> nested && base.get(e.getKey()) instanceof Map<?, ?> existing) {
                deepMerge((Map<String, Object>) existing, (Map<String, Object>) nested);
            } else {
                base.put(e.getKey(), e.getValue());
            }
        }
    }
}
