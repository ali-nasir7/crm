package com.crm.modules.pipeline.repo;

import com.crm.modules.pipeline.domain.LeadStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadStageHistoryRepository extends JpaRepository<LeadStageHistory, UUID> {

    Optional<LeadStageHistory> findFirstByLeadIdAndLeftAtIsNullOrderByEnteredAtDesc(UUID leadId);

    List<LeadStageHistory> findByLeadIdOrderByEnteredAtAsc(UUID leadId);

    @Modifying
    @Query("update LeadStageHistory h set h.leftAt = :now, h.durationSeconds = :seconds where h.leadId = :leadId and h.leftAt is null")
    void closeOpenEntry(UUID leadId, Instant now, long seconds);

    /** Average seconds leads spent per stage (bottleneck analytics). */
    @Query(value = "select to_stage_id as stage, avg(coalesce(duration_seconds, extract(epoch from (now() - entered_at)))) as avg_seconds, count(*) as cnt " +
        "from lead_stage_history where organization_id = :orgId group by to_stage_id", nativeQuery = true)
    List<Object[]> avgSecondsPerStage(UUID orgId);
}
