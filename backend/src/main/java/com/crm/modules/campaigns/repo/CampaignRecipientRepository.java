package com.crm.modules.campaigns.repo;

import com.crm.modules.campaigns.domain.CampaignRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, UUID> {

    Page<CampaignRecipient> findByCampaignIdOrderByCreatedAtAsc(UUID campaignId, Pageable pageable);

    Optional<CampaignRecipient> findByCampaignIdAndLeadId(UUID campaignId, UUID leadId);

    @Query("select r from CampaignRecipient r where r.status = 'IN_PROGRESS' and r.nextSendAt <= :now")
    List<CampaignRecipient> findDueBatch(Instant now, Pageable pageable);

    @Query("select coalesce(count(r), 0) from CampaignRecipient r where r.campaignId = :campaignId and r.status in ('IN_PROGRESS','PENDING')")
    long countRemaining(UUID campaignId);
}
