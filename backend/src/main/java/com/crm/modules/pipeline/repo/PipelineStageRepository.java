package com.crm.modules.pipeline.repo;

import com.crm.modules.pipeline.domain.PipelineStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineStageRepository extends JpaRepository<PipelineStage, UUID> {
    List<PipelineStage> findByPipelineIdOrderByPositionAsc(UUID pipelineId);

    @Query("select s from PipelineStage s where s.pipelineId = :pipelineId and s.type = 'WON'")
    Optional<PipelineStage> findWonStage(UUID pipelineId);

    long countByPipelineId(UUID pipelineId);
}
