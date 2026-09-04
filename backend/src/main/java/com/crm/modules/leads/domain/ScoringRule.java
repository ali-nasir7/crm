package com.crm.modules.leads.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Configurable lead scoring rule. Criterion examples: HAS_EMAIL, HAS_PHONE, HAS_LINKEDIN,
 * DECISION_MAKER_TITLE, INDUSTRY_IN, COUNTRY_IN, COMPANY_SIZE_MIN, CUSTOM_FIELD_IS.
 * Evaluated by LeadScoringService whenever a lead is created/updated.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "scoring_rules", indexes = @Index(name = "ix_scoring_org", columnList = "organization_id"))
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class ScoringRule extends TenantEntity {

    @Column(nullable = false, length = 32)
    private String criterion;

    /** Free-form operand: comma list for IN rules, number for thresholds, key=value for custom fields. */
    @Column(name = "operand", length = 255)
    private String operand;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int position;
}
