package com.crm.modules.leads.repo;

import com.crm.modules.leads.domain.SavedView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {
    List<SavedView> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
