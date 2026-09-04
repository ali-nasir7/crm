package com.crm.modules.campaigns.repo;

import com.crm.modules.campaigns.domain.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID>, JpaSpecificationExecutor<Campaign> {
    Page<Campaign> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
    List<Campaign> findByStatusIn(List<String> statuses);
}
