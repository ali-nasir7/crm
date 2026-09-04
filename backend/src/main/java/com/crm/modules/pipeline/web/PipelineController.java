package com.crm.modules.pipeline.web;

import com.crm.modules.identity.service.PermissionKeys;
import com.crm.modules.pipeline.service.PipelineService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pipelines")
@RequiredArgsConstructor
@Tag(name = "Pipelines")
public class PipelineController {

    private final PipelineService pipelineService;

    public record PipelineRequest(@NotBlank String name, String description, boolean isDefault) {}
    public record StageRequest(@NotBlank String name, String type, Integer probability, Integer position) {}
    public record ReorderRequest(List<UUID> stageIds) {}

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.PIPELINE_VIEW + "')")
    public List<PipelineService.PipelineItem> list() {
        return pipelineService.list(CurrentUser.require().getOrganizationId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.PIPELINE_UPDATE + "')")
    public PipelineService.PipelineItem create(@RequestBody PipelineRequest request) {
        return pipelineService.create(CurrentUser.require().getOrganizationId(), request.name(), request.description(), request.isDefault());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PIPELINE_UPDATE + "')")
    public PipelineService.PipelineItem update(@PathVariable UUID id, @RequestBody PipelineRequest request) {
        return pipelineService.rename(CurrentUser.require().getOrganizationId(), id, request.name(), request.description(), request.isDefault());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PIPELINE_UPDATE + "')")
    public void delete(@PathVariable UUID id) {
        pipelineService.delete(CurrentUser.require().getOrganizationId(), id);
    }

    @PostMapping("/{id}/stages")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PIPELINE_UPDATE + "')")
    public PipelineService.StageItem addStage(@PathVariable UUID id, @RequestBody StageRequest request) {
        return pipelineService.addStage(CurrentUser.require().getOrganizationId(), id, request.name(), request.type(), request.probability(), request.position());
    }

    @PutMapping("/{id}/stages/{stageId}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PIPELINE_UPDATE + "')")
    public PipelineService.StageItem updateStage(@PathVariable UUID id, @PathVariable UUID stageId, @RequestBody StageRequest request) {
        return pipelineService.updateStage(CurrentUser.require().getOrganizationId(), id, stageId, request.name(), request.probability());
    }

    @DeleteMapping("/{id}/stages/{stageId}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PIPELINE_UPDATE + "')")
    public void deleteStage(@PathVariable UUID id, @PathVariable UUID stageId) {
        pipelineService.deleteStage(CurrentUser.require().getOrganizationId(), id, stageId);
    }

    @PutMapping("/{id}/stages/reorder")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PIPELINE_UPDATE + "')")
    public void reorder(@PathVariable UUID id, @RequestBody ReorderRequest request) {
        pipelineService.reorderStages(CurrentUser.require().getOrganizationId(), id, request.stageIds());
    }
}
