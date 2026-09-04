package com.crm.modules.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Global cross-entity search (§44). Each group is limited to the top 5 matches; lead search is visibility-scoped. */
@Service
@RequiredArgsConstructor
public class SearchService {

    public record SearchResultGroup(String type, List<Item> items) {}
    public record Item(UUID id, String title, String subtitle, String url) {}

    private final com.crm.modules.leads.repo.LeadRepository leads;
    private final com.crm.modules.companies.repo.CompanyRepository companies;
    private final com.crm.modules.contacts.repo.ContactRepository contacts;
    private final com.crm.modules.deals.repo.DealRepository deals;
    private final com.crm.modules.proposals.repo.ProposalRepository proposals;
    private final com.crm.modules.tasks.repo.TaskRepository tasks;
    private final com.crm.modules.leads.service.LeadAccessPolicy leadAccess;

    @Transactional(readOnly = true)
    public List<SearchResultGroup> search(UUID orgId, com.crm.security.UserPrincipal principal, String q) {
        String like = "%" + q.toLowerCase().trim() + "%";
        List<SearchResultGroup> groups = new ArrayList<>();
        var page = org.springframework.data.domain.PageRequest.of(0, 5);

        var leadSpec = leadAccess.visibility(orgId, principal)
            .and((root, cq, cb) -> cb.or(
                cb.like(cb.lower(root.get("businessName")), like),
                cb.like(cb.lower(root.get("email")), like),
                cb.like(cb.lower(root.get("phone")), like),
                cb.like(cb.lower(root.get("city")), like),
                cb.like(cb.lower(root.get("firstName")), like),
                cb.like(cb.lower(root.get("lastName")), like)));
        groups.add(new SearchResultGroup("leads", leads.findAll(leadSpec, page)
            .map(l -> new Item(l.getId(), l.getBusinessName(), nz(l.getCity()) + (l.getEmail() != null ? " · " + l.getEmail() : ""), "/leads/" + l.getId()))
            .getContent()));

        groups.add(new SearchResultGroup("companies", companies.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("website")), like))), page)
            .map(c -> new Item(c.getId(), c.getName(), nz(c.getWebsite()), "/companies/" + c.getId())).getContent()));

        groups.add(new SearchResultGroup("contacts", contacts.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.or(cb.like(cb.lower(root.get("firstName")), like), cb.like(cb.lower(root.get("lastName")), like),
                    cb.like(cb.lower(root.get("email")), like))), page)
            .map(c -> new Item(c.getId(), c.displayName(), nz(c.getEmail()), "/contacts/" + c.getId())).getContent()));

        groups.add(new SearchResultGroup("deals", deals.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.like(cb.lower(root.get("title")), like)), page)
            .map(d -> new Item(d.getId(), d.getTitle(), d.getStatus(), "/deals/" + d.getId())).getContent()));

        groups.add(new SearchResultGroup("proposals", proposals.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.like(cb.lower(root.get("title")), like)), page)
            .map(p -> new Item(p.getId(), p.getProposalNumber() + " — " + p.getTitle(), p.getStatus(), "/proposals/" + p.getId())).getContent()));

        groups.add(new SearchResultGroup("tasks", tasks.findAll((root, cq, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.like(cb.lower(root.get("title")), like)), page)
            .map(t -> new Item(t.getId(), t.getTitle(), t.getStatus(), "/tasks")).getContent()));

        return groups.stream().filter(g -> !g.items().isEmpty()).toList();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
