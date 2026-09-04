package com.crm.modules.companies.service;

import com.crm.common.api.ApiException;
import com.crm.common.api.PageResponse;
import com.crm.modules.audit.service.AuditService;
import com.crm.modules.companies.domain.Company;
import com.crm.modules.companies.dto.CompanyDtos.*;
import com.crm.modules.companies.repo.CompanyRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.repo.TagRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companies;
    private final TagRepository tags;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<CompanyItem> list(UUID orgId, String q, String industry, String country, String city,
                                          UUID ownerId, int page, int size) {
        Specification<Company> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("organizationId"), orgId));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("website")), like),
                    cb.like(cb.lower(root.get("email")), like)));
            }
            if (industry != null && !industry.isBlank()) ps.add(cb.equal(cb.lower(root.get("industry")), industry.toLowerCase()));
            if (country != null && !country.isBlank()) ps.add(cb.equal(cb.lower(root.get("country")), country.toLowerCase()));
            if (city != null && !city.isBlank()) ps.add(cb.equal(cb.lower(root.get("city")), city.toLowerCase()));
            if (ownerId != null) ps.add(cb.equal(root.get("ownerId"), ownerId));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<CompanyItem> result = companies.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")))
            .map(c -> toItem(c));
        return PageResponse.of(result.getContent(), result.getPageable(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CompanyItem get(UUID orgId, UUID id) {
        return toItem(find(orgId, id));
    }

    @Transactional
    public CompanyItem create(UUID orgId, CreateCompanyRequest req) {
        Company c = new Company();
        c.setOrganizationId(orgId);
        apply(c, req.name(), req.website(), req.industry(), req.description(), req.phone(), req.email(),
            req.country(), req.city(), req.state(), req.address(), req.linkedin(), req.companySize(), req.annualRevenue(), req.ownerId());
        resolveTags(orgId, c, req.tags());
        companies.save(c);
        audit.log("COMPANY_CREATE", "COMPANY", c.getId(), c.getName(), null, null);
        return toItem(c);
    }

    @Transactional
    public CompanyItem update(UUID orgId, UUID id, UpdateCompanyRequest req) {
        Company c = find(orgId, id);
        Map<String, Object> before = Map.of("name", c.getName(), "industry", str(c.getIndustry()));
        apply(c, req.name(), req.website(), req.industry(), req.description(), req.phone(), req.email(),
            req.country(), req.city(), req.state(), req.address(), req.linkedin(), req.companySize(), req.annualRevenue(), req.ownerId());
        resolveTags(orgId, c, req.tags());
        companies.save(c);
        audit.log("COMPANY_UPDATE", "COMPANY", c.getId(), c.getName(), before, Map.of("name", c.getName()));
        return toItem(c);
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Company c = find(orgId, id);
        c.setDeletedAt(Instant.now());
        companies.save(c);
        audit.log("COMPANY_DELETE", "COMPANY", c.getId(), c.getName(), null, null);
    }

    private void apply(Company c, String name, String website, String industry, String description, String phone,
                       String email, String country, String city, String state, String address, String linkedin,
                       String companySize, String annualRevenue, UUID ownerId) {
        if (name != null) {
            if (name.isBlank()) throw ApiException.badRequest("Company name is required");
            c.setName(name.trim());
        }
        c.setWebsite(website);
        c.setIndustry(industry);
        c.setDescription(description);
        c.setPhone(phone);
        c.setEmail(email);
        c.setCountry(country);
        c.setCity(city);
        c.setState(state);
        c.setAddress(address);
        c.setLinkedin(linkedin);
        c.setCompanySize(companySize);
        c.setAnnualRevenue(annualRevenue);
        if (ownerId != null) {
            users.findById(ownerId).filter(u -> u.getOrganizationId().equals(c.getOrganizationId()))
                .orElseThrow(() -> ApiException.badRequest("Owner is not part of this organization"));
            c.setOwnerId(ownerId);
        }
    }

    void resolveTags(UUID orgId, Company c, List<String> tagNames) {
        if (tagNames == null) return;
        Set<com.crm.modules.leads.domain.Tag> resolved = new LinkedHashSet<>();
        for (String name : tagNames) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            var tag = tags.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .filter(t -> t.getName().equalsIgnoreCase(trimmed)).findFirst()
                .orElseGet(() -> {
                    var t = new com.crm.modules.leads.domain.Tag();
                    t.setOrganizationId(orgId);
                    t.setName(trimmed);
                    return tags.save(t);
                });
            resolved.add(tag);
        }
        c.getTags().clear();
        c.getTags().addAll(resolved);
    }

    private Company find(UUID orgId, UUID id) {
        return companies.findById(id).filter(c -> c.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Company not found"));
    }

    public CompanyItem toItem(Company c) {
        return new CompanyItem(c.getId(), c.getName(), c.getWebsite(), c.getIndustry(), c.getDescription(),
            c.getPhone(), c.getEmail(), c.getCountry(), c.getCity(), c.getState(), c.getAddress(),
            c.getLinkedin(), c.getCompanySize(), c.getAnnualRevenue(), c.getOwnerId(),
            c.getOwnerId() != null ? users.findById(c.getOwnerId()).map(u -> u.displayName()).orElse(null) : null,
            c.getTags().stream().map(t -> t.getName()).toList(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private String str(String s) { return s == null ? "" : s; }
}
