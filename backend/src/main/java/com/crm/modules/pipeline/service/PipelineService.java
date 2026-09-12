package com.crm.modules.pipeline.service;

import com.crm.common.api.ApiException;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.pipeline.domain.LeadStageHistory;
import com.crm.modules.pipeline.domain.Pipeline;
import com.crm.modules.pipeline.domain.PipelineStage;
import com.crm.modules.pipeline.repo.LeadStageHistoryRepository;
import com.crm.modules.pipeline.repo.PipelineRepository;
import com.crm.modules.pipeline.repo.PipelineStageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineRepository pipelines;
    private final PipelineStageRepository stages;
    private final LeadStageHistoryRepository history;

    public record StageItem(UUID id, UUID pipelineId, String name, int position, String type, int probability) {}
    public record PipelineItem(UUID id, String name, String description, boolean isDefault, List<StageItem> stages) {}

    @Transactional(readOnly = true)
    public List<PipelineItem> list(UUID orgId) {
        return pipelines.findByOrganizationIdOrderByCreatedAtAsc(orgId).stream().map(this::toItem).toList();
    }

    @Transactional
    public PipelineItem create(UUID orgId, String name, String description, boolean isDefault) {
        Pipeline p = new Pipeline();
        p.setOrganizationId(orgId);
        p.setName(name);
        p.setDescription(description);
        if (isDefault) pipelines.findByOrganizationIdOrderByCreatedAtAsc(orgId).forEach(x -> x.setDefault(false));
        p.setDefault(isDefault);
        pipelines.save(p);
        return toItem(p);
    }

    @Transactional
    public PipelineItem rename(UUID orgId, UUID id, String name, String description, Boolean isDefault) {
        Pipeline p = find(orgId, id);
        if (name != null) p.setName(name);
        if (description != null) p.setDescription(description);
        if (Boolean.TRUE.equals(isDefault)) {
            pipelines.findByOrganizationIdOrderByCreatedAtAsc(orgId).forEach(x -> x.setDefault(false));
            p.setDefault(true);
        }
        return toItem(p);
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Pipeline p = find(orgId, id);
        if (stages.countByPipelineId(id) > 0 && stages.findByPipelineIdOrderByPositionAsc(id).stream()
                .anyMatch(s -> "OPEN".equals(s.getType()))) {
            throw ApiException.business("Reassign leads before deleting a pipeline with open stages");
        }
        p.setDeletedAt(Instant.now());
        pipelines.save(p);
    }

    @Transactional
    public StageItem addStage(UUID orgId, UUID pipelineId, String name, String type, Integer probability, Integer position) {
        find(orgId, pipelineId);
        List<PipelineStage> existing = stages.findByPipelineIdOrderByPositionAsc(pipelineId);
        PipelineStage s = new PipelineStage();
        s.setPipelineId(pipelineId);
        s.setName(name);
        s.setType(type == null ? "OPEN" : type.toUpperCase());
        s.setProbability(probability == null ? ("WON".equalsIgnoreCase(s.getType()) ? 100 : 0) : probability);
        s.setPosition(position == null ? existing.size() : Math.min(position, existing.size()));
        stages.save(s);
        // shift positions after insert point
        existing.stream().filter(x -> !x.getId().equals(s.getId()) && x.getPosition() >= s.getPosition())
            .forEach(x -> { x.setPosition(x.getPosition() + 1); stages.save(x); });
        return toStageItem(s);
    }

    @Transactional
    public StageItem updateStage(UUID orgId, UUID pipelineId, UUID stageId, String name, Integer probability) {
        find(orgId, pipelineId);
        PipelineStage s = stages.findById(stageId).orElseThrow(() -> ApiException.notFound("Stage not found"));
        if (!s.getPipelineId().equals(pipelineId)) throw ApiException.badRequest("Stage not in pipeline");
        if (name != null) s.setName(name);
        if (probability != null) s.setProbability(probability);
        return toStageItem(s);
    }

    @Transactional
    public void reorderStages(UUID orgId, UUID pipelineId, List<UUID> orderedIds) {
        find(orgId, pipelineId);
        List<PipelineStage> all = stages.findByPipelineIdOrderByPositionAsc(pipelineId);
        if (orderedIds.size() != all.size()) throw ApiException.badRequest("Reorder payload must contain every stage");
        int pos = 0;
        for (UUID id : orderedIds) {
            PipelineStage s = all.stream().filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Stage not in pipeline"));
            s.setPosition(pos++);
            stages.save(s);
        }
    }

    @Transactional
    public void deleteStage(UUID orgId, UUID pipelineId, UUID stageId) {
        find(orgId, pipelineId);
        PipelineStage s = stages.findById(stageId).orElseThrow(() -> ApiException.notFound("Stage not found"));
        if (!s.getPipelineId().equals(pipelineId)) throw ApiException.badRequest("Stage not in pipeline");
        long count = stages.countByPipelineId(pipelineId);
        if (count <= 1) throw ApiException.business("A pipeline needs at least one stage");
        stages.delete(s);
    }

    // ---- stage history (called by LeadService) ----

    @Transactional
    public void openStageEntry(Lead lead) {
        if (lead.getStageId() == null) return;
        LeadStageHistory h = new LeadStageHistory();
        h.setLeadId(lead.getId());
        h.setOrganizationId(lead.getOrganizationId());
        h.setToStageId(lead.getStageId());
        h.setChangedBy(lead.getCreatedBy());
        h.setEnteredAt(Instant.now());
        history.save(h);
    }

    @Transactional
    public void recordTransition(Lead lead, UUID fromStageId, UUID toStageId, UUID changedBy) {
        Instant now = Instant.now();
        history.findFirstByLeadIdAndLeftAtIsNullOrderByEnteredAtDesc(lead.getId()).ifPresent(open -> {
            open.setLeftAt(now);
            open.setDurationSeconds(Duration.between(open.getEnteredAt(), now).getSeconds());
            history.save(open);
        });
        LeadStageHistory h = new LeadStageHistory();
        h.setLeadId(lead.getId());
        h.setOrganizationId(lead.getOrganizationId());
        h.setFromStageId(fromStageId);
        h.setToStageId(toStageId);
        h.setChangedBy(changedBy);
        h.setEnteredAt(now);
        history.save(h);
    }

    private Pipeline find(UUID orgId, UUID id) {
        return pipelines.findById(id).filter(p -> p.getOrganizationId().equals(orgId))
            .orElseThrow(() -> ApiException.notFound("Pipeline not found"));
    }

    private PipelineItem toItem(Pipeline p) {
        return new PipelineItem(p.getId(), p.getName(), p.getDescription(), p.isDefault(),
            stages.findByPipelineIdOrderByPositionAsc(p.getId()).stream().map(this::toStageItem).toList());
    }

    private StageItem toStageItem(PipelineStage s) {
        return new StageItem(s.getId(), s.getPipelineId(), s.getName(), s.getPosition(), s.getType(), s.getProbability());
    }
}
