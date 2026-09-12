package com.crm.modules.leads.repo;

import com.crm.modules.leads.domain.CustomFieldDef;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomFieldDefRepository extends JpaRepository<CustomFieldDef, UUID> {
    /** EntityGraph keeps the LAZY {@code options} inside the one query - no
     *  LazyInitializationException during serialization (open-in-view is off) and no N+1. */
    @EntityGraph(attributePaths = "options")
    List<CustomFieldDef> findByOrganizationIdOrderByPositionAsc(UUID organizationId);
}
