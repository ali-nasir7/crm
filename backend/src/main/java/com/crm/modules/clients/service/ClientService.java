package com.crm.modules.clients.service;

import com.crm.common.api.ApiException;
import com.crm.common.api.PageResponse;
import com.crm.modules.clients.domain.Client;
import com.crm.modules.clients.dto.ClientDtos.*;
import com.crm.modules.clients.repo.ClientRepository;
import com.crm.modules.companies.repo.CompanyRepository;
import com.crm.modules.contacts.repo.ContactRepository;
import com.crm.modules.deals.repo.DealRepository;
import com.crm.modules.identity.repo.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "ONBOARDING", "AT_RISK", "INACTIVE", "CHURNED");

    private final ClientRepository clients;
    private final CompanyRepository companies;
    private final ContactRepository contacts;
    private final UserRepository users;
    private final DealRepository deals;

    @Transactional(readOnly = true)
    public PageResponse<ClientItem> list(UUID orgId, String status, String q, int page, int size) {
        Specification<Client> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("organizationId"), orgId));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status.toUpperCase()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<Client> result = clients.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "convertedAt")));
        List<ClientItem> items = result.getContent().stream().map(this::toItem).toList();
        return PageResponse.of(items, result.getPageable(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ClientItem get(UUID orgId, UUID id) {
        return toItem(find(orgId, id));
    }

    @Transactional
    public ClientItem update(UUID orgId, UUID id, UpdateClientRequest req) {
        Client c = find(orgId, id);
        if (req.accountManagerId() != null) {
            users.findById(req.accountManagerId()).filter(u -> u.getOrganizationId().equals(orgId))
                .orElseThrow(() -> ApiException.badRequest("User not in organization"));
            c.setAccountManagerId(req.accountManagerId());
        }
        if (req.status() != null) {
            String s = req.status().toUpperCase();
            if (!STATUSES.contains(s)) throw ApiException.badRequest("Invalid client status");
            c.setStatus(s);
        }
        if (req.notes() != null) c.setNotes(req.notes());
        return toItem(c);
    }

    /** Recompute lifetime value from won deals (called after deal close). */
    @Transactional
    public void recomputeLifetimeValue(UUID orgId, UUID clientId) {
        clients.findById(clientId).ifPresent(c -> {
            var ltv = deals.sumWonForClient(orgId, clientId);
            c.setLifetimeValue(ltv);
        });
    }

    private Client find(UUID orgId, UUID id) {
        return clients.findById(id).filter(c -> c.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Client not found"));
    }

    public ClientItem toItem(Client c) {
        var company = companies.findById(c.getCompanyId()).orElse(null);
        var contact = c.getPrimaryContactId() != null ? contacts.findById(c.getPrimaryContactId()).orElse(null) : null;
        return new ClientItem(c.getId(), c.getCompanyId(), company != null ? company.getName() : null,
            company != null ? company.getWebsite() : null, c.getPrimaryContactId(),
            contact != null ? contact.displayName() : null, c.getAccountManagerId(),
            c.getAccountManagerId() != null ? users.findById(c.getAccountManagerId()).map(u -> u.displayName()).orElse(null) : null,
            c.getStatus(), c.getLifetimeValue(), c.getConvertedFromLeadId(), c.getConvertedAt(), c.getNotes(), c.getCreatedAt());
    }
}
