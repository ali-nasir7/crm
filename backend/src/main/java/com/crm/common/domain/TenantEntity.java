package com.crm.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

/**
 * Base for all tenant-owned records. The organization id is NEVER taken from request input;
 * it is set server-side and enforced by the tenantFilter (enabled per-request by TenantScopeAspect)
 * plus explicit repository scoping. See SECURITY.md.
 */
@Getter
@Setter
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "orgId", type = String.class))
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
}
