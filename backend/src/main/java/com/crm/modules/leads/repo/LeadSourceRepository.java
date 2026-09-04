package com.crm.modules.leads.repo;

import com.crm.modules.leads.domain.LeadSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadSourceRepository extends JpaRepository<LeadSource, UUID> {
    List<LeadSource> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    Optional<LeadSource> findByOrganizationIdAndKey(UUID organizationId, String key);
}
