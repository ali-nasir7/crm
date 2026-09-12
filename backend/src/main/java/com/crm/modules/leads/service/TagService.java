package com.crm.modules.leads.service;

import com.crm.common.api.ApiException;
import com.crm.modules.leads.domain.Tag;
import com.crm.modules.leads.repo.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tag management. Business rules live here (not in the controller):
 * name normalisation, duplicate detection (409 with a clear message),
 * soft delete with revive, and tenant-scoped writes.
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tags;

    @Transactional(readOnly = true)
    public List<Tag> list(UUID orgId) {
        return tags.findByOrganizationIdOrderByNameAsc(orgId);
    }

    @Transactional
    public Tag create(UUID orgId, String name, String color) {
        String n = clean(name);
        var existing = tags.findAnyByNameIncludingDeleted(orgId, n);
        if (existing.isPresent()) {
            Tag t = existing.get();
            if (t.getDeletedAt() != null) {
                // same name was used before and deleted: revive instead of hitting uk_tags_org_name
                t.setDeletedAt(null);
                t.setColor(color);
                return tags.save(t);
            }
            throw ApiException.conflict("Tag \"" + n + "\" already exists");
        }
        Tag t = new Tag();
        t.setOrganizationId(orgId);
        t.setName(n);
        t.setColor(color);
        return tags.save(t);
    }

    @Transactional
    public Tag update(UUID orgId, UUID id, String name, String color) {
        Tag t = findInOrg(orgId, id);
        String n = clean(name);
        tags.findAnyByNameIncludingDeleted(orgId, n)
            .filter(other -> !other.getId().equals(id))
            .ifPresent(other -> { throw ApiException.conflict("Tag \"" + n + "\" already exists"); });
        t.setName(n);
        t.setColor(color);
        return tags.save(t);
    }

    /** Soft delete: keeps lead_tags history intact and lets the name be revived later. */
    @Transactional
    public void delete(UUID orgId, UUID id) {
        Tag t = findInOrg(orgId, id);
        t.setDeletedAt(Instant.now());
        tags.save(t);
    }

    private Tag findInOrg(UUID orgId, UUID id) {
        return tags.findInOrg(orgId, id).orElseThrow(() -> ApiException.notFound("Tag not found"));
    }

    private String clean(String name) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) throw ApiException.badRequest("Tag name is required");
        if (n.length() > 48) throw ApiException.badRequest("Tag name must be 48 characters or fewer");
        return n;
    }
}
