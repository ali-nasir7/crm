package com.crm.modules.bulk.service;

import com.crm.common.api.ApiException;
import com.crm.modules.bulk.domain.BulkJob;
import com.crm.modules.bulk.repo.BulkJobRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.leads.service.LeadAccessPolicy;
import com.crm.modules.leads.service.LeadScoringService;
import com.crm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Bulk lead operations executed in the background. Every item is re-checked against the caller's
 * visibility scope when possible (the snapshot of ids is validated at enqueue time for OWN scope).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkService {

    private final BulkJobRepository jobs;
    private final LeadRepository leads;
    private final LeadAccessPolicy accessPolicy;
    private final UserRepository users;
    private final LeadScoringService scoring;
    private final com.crm.modules.leads.repo.TagRepository tagRepository;

    public record BulkJobItem(UUID id, String jobType, Map<String, Object> params, int totalCount,
                              int processedCount, int successCount, int failedCount, String status,
                              String errorMessage, Instant createdAt, Instant completedAt) {}

    @Transactional
    public BulkJobItem enqueue(UUID orgId, UUID actorId, String action, List<UUID> leadIds, Map<String, Object> params) {
        String type = action.toUpperCase().replace("-", "_");
        Set<String> allowed = Set.of("ASSIGN", "STAGE", "STATUS", "ADD_TAG", "REMOVE_TAG", "DELETE");
        if (!allowed.contains(type)) throw ApiException.badRequest("Unknown bulk action: " + action);
        if (leadIds == null || leadIds.isEmpty()) throw ApiException.badRequest("No leads selected");

        // validate visibility for every lead before accepting the job (rep-scoped safety)
        var scope = accessPolicy.scopeOf(CurrentUser.require(), orgId);
        if (scope != com.crm.modules.identity.domain.DataScope.ORG) {
            Set<UUID> visible = accessPolicy.visibleUserIds(orgId, CurrentUser.require());
            for (UUID id : leadIds) {
                boolean ok = leads.findById(id).filter(l -> l.getOrganizationId().equals(orgId))
                    .map(l -> l.getAssignedUserId() == null || visible.contains(l.getAssignedUserId()))
                    .orElse(false);
                if (!ok) throw ApiException.forbidden("Some selected leads are outside your visibility");
            }
        }

        BulkJob job = new BulkJob();
        job.setOrganizationId(orgId);
        job.setJobType(type);
        job.setParams(params == null ? Map.of() : params);
        job.setTotalCount(leadIds.size());
        jobs.save(job);
        params = job.getParams();
        Map<String, Object> withIds = new LinkedHashMap<>(params);
        withIds.put("leadIds", leadIds);
        job.setParams(withIds);
        jobs.saveAndFlush(job);
        return toItem(job);
    }

    @Async("ioTaskExecutor")
    @Transactional
    public void run(UUID orgId, UUID jobId) {
        BulkJob job = jobs.findById(jobId).orElse(null);
        if (job == null || !"PENDING".equals(job.getStatus())) return;
        job.setStatus("RUNNING");
        jobs.saveAndFlush(job);
        try {
            List<UUID> ids = ((List<?>) job.getParams().get("leadIds")).stream().map(x -> UUID.fromString(x.toString())).toList();
            int success = 0, failed = 0;
            for (UUID leadId : ids) {
                try {
                    apply(orgId, job, leadId);
                    success++;
                } catch (Exception e) {
                    failed++;
                }
                job.setProcessedCount(job.getProcessedCount() + 1);
                job.setSuccessCount(success);
                job.setFailedCount(failed);
                jobs.saveAndFlush(job); // observable progress
            }
            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            jobs.save(job);
        } catch (Exception e) {
            log.error("Bulk job failed", e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            jobs.save(job);
        }
    }

    private void apply(UUID orgId, BulkJob job, UUID leadId) {
        Optional<Lead> opt = leads.findInOrg(orgId, leadId);
        if (opt.isEmpty()) throw new NoSuchElementException("lead not found");
        Lead lead = opt.get();
        Map<String, Object> params = job.getParams();
        switch (job.getJobType()) {
            case "ASSIGN" -> {
                UUID userId = UUID.fromString(params.get("userId").toString());
                lead.setAssignedUserId(userId);
            }
            case "STAGE" -> {
                UUID stageId = UUID.fromString(params.get("stageId").toString());
                lead.setStageId(stageId);
            }
            case "STATUS" -> {
                lead.setStatus(com.crm.modules.leads.domain.LeadStatus.valueOf(params.get("status").toString().toUpperCase()));
            }
            case "ADD_TAG", "REMOVE_TAG" -> {
                String tagName = String.valueOf(params.get("tag"));
                var tag = tagRepository.findByOrganizationIdOrderByNameAsc(orgId).stream()
                    .filter(t -> t.getName().equalsIgnoreCase(tagName)).findFirst()
                    .orElseGet(() -> {
                        if ("REMOVE_TAG".equals(job.getJobType())) return null;
                        var t = new com.crm.modules.leads.domain.Tag();
                        t.setOrganizationId(orgId);
                        t.setName(tagName);
                        return tagRepository.save(t);
                    });
                if (tag != null) {
                    if ("ADD_TAG".equals(job.getJobType())) lead.getTags().add(tag);
                    else lead.getTags().remove(tag);
                }
            }
            case "DELETE" -> lead.setDeletedAt(Instant.now());
            default -> throw new IllegalStateException("unknown action");
        }
        leads.save(lead);
    }

    @Transactional(readOnly = true)
    public com.crm.common.api.PageResponse<BulkJobItem> list(UUID orgId, int page, int size) {
        var result = jobs.findByOrganizationIdOrderByCreatedAtDesc(orgId, PageRequest.of(page, Math.min(size, 50)));
        return com.crm.common.api.PageResponse.of(result.map(this::toItem));
    }

    private BulkJobItem toItem(BulkJob j) {
        return new BulkJobItem(j.getId(), j.getJobType(), j.getParams(), j.getTotalCount(), j.getProcessedCount(),
            j.getSuccessCount(), j.getFailedCount(), j.getStatus(), j.getErrorMessage(), j.getCreatedAt(), j.getCompletedAt());
    }
}
