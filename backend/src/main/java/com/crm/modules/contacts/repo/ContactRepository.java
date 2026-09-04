package com.crm.modules.contacts.repo;

import com.crm.modules.contacts.domain.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID>, JpaSpecificationExecutor<Contact> {

    @Query("select c from Contact c where c.organizationId = :orgId and lower(c.email) = lower(:email)")
    List<Contact> findByEmail(UUID orgId, String email);

    @Query("select c from Contact c where c.organizationId = :orgId and c.companyId = :companyId")
    List<Contact> findByCompanyId(UUID orgId, UUID companyId);
}
