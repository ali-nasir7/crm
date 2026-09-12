package com.crm.modules.email.repo;

import com.crm.modules.email.domain.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {
    List<EmailTemplate> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
