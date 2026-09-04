package com.crm.modules.campaigns.repo;

import com.crm.modules.campaigns.domain.CampaignStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CampaignStepRepository extends JpaRepository<CampaignStep, UUID> {
    List<CampaignStep> findByCampaignIdOrderByPositionAsc(UUID campaignId);
    void deleteByCampaignId(UUID campaignId);
}
