package com.crm.modules.ai.domain;

import com.crm.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/** Every AI generation is logged: who asked, for which lead, which provider, what came out. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "ai_actions", indexes = @Index(name = "ix_ai_org", columnList = "organization_id, created_at"))
public class AiAction extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "use_case", nullable = false, length = 40)
    private String useCase;

    @Column(name = "provider", nullable = false, length = 24)
    private String provider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output", columnDefinition = "jsonb")
    private Map<String, Object> output;
}
