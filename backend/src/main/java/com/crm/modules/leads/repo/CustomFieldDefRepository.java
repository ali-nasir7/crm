package com.crm.modules.leads.repo;

import com.crm.modules.leads.domain.CustomFieldDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomFieldDefRepository extends JpaRepository<CustomFieldDef, UUID> {
    List<CustomFieldDef> findByOrganizationIdOrderByPositionAsc(UUID organizationId);
}
