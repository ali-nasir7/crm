package com.crm.modules.contacts.service;

import com.crm.common.api.PageResponse;
import com.crm.modules.companies.repo.CompanyRepository;
import com.crm.modules.contacts.domain.Contact;
import com.crm.modules.contacts.dto.ContactDtos.*;
import com.crm.modules.contacts.repo.ContactRepository;
import com.crm.modules.identity.repo.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contacts;
    private final CompanyRepository companies;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public PageResponse<ContactItem> list(UUID orgId, String q, UUID companyId, int page, int size) {
        Specification<Contact> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("organizationId"), orgId));
            if (companyId != null) ps.add(cb.equal(root.get("companyId"), companyId));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("firstName")), like), cb.like(cb.lower(root.get("lastName")), like),
                    cb.like(cb.lower(root.get("email")), like), cb.like(cb.lower(root.get("phone")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<ContactItem> result = contacts.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")))
            .map(this::toItem);
        return PageResponse.of(result.getContent(), result.getPageable(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ContactItem get(UUID orgId, UUID id) {
        return toItem(find(orgId, id));
    }

    @Transactional
    public ContactItem create(UUID orgId, CreateContactRequest req) {
        Contact c = new Contact();
        c.setOrganizationId(orgId);
        apply(c, req.companyId(), req.firstName(), req.lastName(), req.jobTitle(), req.email(), req.secondaryEmail(),
            req.phone(), req.whatsapp(), req.linkedin(), req.ownerId(), req.primary(), req.notes());
        c.setPrimary(true);
        contacts.save(c);
        return toItem(c);
    }

    @Transactional
    public ContactItem update(UUID orgId, UUID id, UpdateContactRequest req) {
        Contact c = find(orgId, id);
        apply(c, req.companyId(), req.firstName(), req.lastName(), req.jobTitle(), req.email(), req.secondaryEmail(),
            req.phone(), req.whatsapp(), req.linkedin(), req.ownerId(),
            req.primary() != null ? req.primary() : c.isPrimary(), req.notes());
        contacts.save(c);
        return toItem(c);
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Contact c = find(orgId, id);
        c.setDeletedAt(Instant.now());
        contacts.save(c);
    }

    @Transactional(readOnly = true)
    public List<Contact> findByCompany(UUID orgId, UUID companyId) {
        return contacts.findByCompanyId(orgId, companyId);
    }

    private void apply(Contact c, UUID companyId, String firstName, String lastName, String jobTitle, String email,
                       String secondaryEmail, String phone, String whatsapp, String linkedin, UUID ownerId,
                       boolean primary, String notes) {
        c.setCompanyId(companyId);
        c.setFirstName(firstName.trim());
        c.setLastName(lastName.trim());
        c.setJobTitle(jobTitle);
        c.setEmail(email);
        c.setSecondaryEmail(secondaryEmail);
        c.setPhone(phone);
        c.setWhatsapp(whatsapp);
        c.setLinkedin(linkedin);
        c.setOwnerId(ownerId);
        c.setPrimary(primary);
        c.setNotes(notes);
    }

    private Contact find(UUID orgId, UUID id) {
        return contacts.findById(id).filter(c -> c.getOrganizationId().equals(orgId))
            .orElseThrow(() -> com.crm.common.api.ApiException.notFound("Contact not found"));
    }

    public ContactItem toItem(Contact c) {
        return new ContactItem(c.getId(), c.getCompanyId(),
            c.getCompanyId() != null ? companies.findById(c.getCompanyId()).map(m -> m.getName()).orElse(null) : null,
            c.getFirstName(), c.getLastName(), c.displayName(), c.getJobTitle(), c.getEmail(), c.getSecondaryEmail(),
            c.getPhone(), c.getWhatsapp(), c.getLinkedin(), c.getOwnerId(),
            c.getOwnerId() != null ? users.findById(c.getOwnerId()).map(u -> u.displayName()).orElse(null) : null,
            c.isPrimary(), c.getNotes(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
