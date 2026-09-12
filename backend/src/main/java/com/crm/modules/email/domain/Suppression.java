package com.crm.modules.email.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/** Organization-level do-not-contact list: unsubscribes, bounces, complaints, manual blocks. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "suppressions",
    uniqueConstraints = @UniqueConstraint(name = "uk_suppressions_org_email", columnNames = {"organization_id", "email"}),
    indexes = @Index(name = "ix_suppressions_org", columnList = "organization_id"))
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class Suppression extends TenantEntity {

    public enum Reason { UNSUBSCRIBE, BOUNCE, COMPLAINT, MANUAL }

    @Column(nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Reason reason;

    @Column(length = 500)
    private String note;
}
