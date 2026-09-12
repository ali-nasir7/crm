package com.crm.modules.leads.service;

import com.crm.common.api.ApiException;
import com.crm.modules.identity.domain.DataScope;
import com.crm.modules.identity.repo.RoleRepository;
import com.crm.modules.identity.repo.TeamRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.security.CurrentUser;
import com.crm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Data-visibility enforcement (SECURITY.md, authorization layer 2). A principal may only see leads
 * within the intersection of their tenant and their role's DataScope:
 *   OWN  → only leads assigned to them (or created by them)
 *   TEAM → leads assigned to anyone in the teams they manage or belong to
 *   ORG  → all leads of the organization
 * The scope comes from role data (roles.data_scope), so custom roles are fully configurable.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeadAccessPolicy {

    private final LeadRepository leads;
    private final TeamRepository teams;
    private final RoleRepository roles;

    public DataScope scopeOf(UserPrincipal p, UUID orgId) {
        if (p.isSuperAdmin()) return DataScope.ORG;
        DataScope widest = DataScope.OWN;
        for (String roleKey : p.getRoles()) {
            DataScope s = roles.findByOrganizationIdAndKey(orgId, roleKey)
                .map(com.crm.modules.identity.domain.Role::getDataScope)
                .orElse(DataScope.OWN);
            if (s == DataScope.ORG) return DataScope.ORG;
            if (s == DataScope.TEAM) widest = DataScope.TEAM;
        }
        return widest;
    }

    /** Specification restricting a lead query to everything the principal may see. */
    public Specification<Lead> visibility(UUID orgId, UserPrincipal p) {
        return (root, cq, cb) -> {
            var org = cb.equal(root.get("organizationId"), orgId);
            DataScope scope = scopeOf(p, orgId);
            if (scope == DataScope.ORG) return org;
            Set<UUID> visibleUsers = scope == DataScope.OWN ? Set.of(p.getId()) : visibleUserIds(orgId, p);
            if (visibleUsers.isEmpty()) visibleUsers = Set.of(p.getId());
            return cb.and(org, root.get("assignedUserId").in(visibleUsers));
        };
    }

    /** Users whose leads the principal can see under their best scope. */
    public Set<UUID> visibleUserIds(UUID orgId, UserPrincipal p) {
        Set<UUID> ids = new HashSet<>();
        ids.add(p.getId());
        teams.findByOrganizationIdOrderByNameAsc(orgId).forEach(t -> {
            boolean related = p.getId().equals(t.getManagerId()) ||
                t.getMembers().stream().anyMatch(m -> m.getId().equals(p.getId()));
            if (related) {
                if (t.getManagerId() != null) ids.add(t.getManagerId());
                t.getMembers().forEach(m -> ids.add(m.getId()));
            }
        });
        return ids;
    }

    public Lead loadVisible(UUID orgId, UUID leadId) {
        UserPrincipal p = CurrentUser.require();
        Lead lead = leads.findOne((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.equal(root.get("id"), leadId)))
            .orElseThrow(() -> ApiException.notFound("Lead not found"));
        if (!canSee(lead, orgId, p)) throw ApiException.notFound("Lead not found");
        return lead;
    }

    public void assertCanAccess(UUID orgId, UUID leadId) {
        loadVisible(orgId, leadId);
    }

    private boolean canSee(Lead lead, UUID orgId, UserPrincipal p) {
        DataScope scope = scopeOf(p, orgId);
        return switch (scope) {
            case ORG -> true;
            case TEAM -> lead.getAssignedUserId() == null || visibleUserIds(orgId, p).contains(lead.getAssignedUserId());
            case OWN -> p.getId().equals(lead.getAssignedUserId()) || p.getId().equals(lead.getCreatedBy());
        };
    }
}
