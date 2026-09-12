package com.crm.modules.pipeline.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "pipeline_stages", indexes = @Index(name = "ix_stages_pipeline", columnList = "pipeline_id"))
public class PipelineStage {

    @Id
    @jakarta.persistence.GeneratedValue(strategy = GenerationType.UUID)
    @org.hibernate.annotations.UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private int position;

    /** OPEN, WON, LOST */
    @Column(nullable = false, length = 8)
    private String type = "OPEN";

    @Column(nullable = false)
    private int probability; // 0-100, default close probability
}
