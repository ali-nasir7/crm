package com.crm.modules.calling.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A user's personal calling device (an Android phone reached through the bridge app). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "calling_devices", uniqueConstraints =
    @UniqueConstraint(name = "uk_calling_device_org_name", columnNames = {"user_id", "device_name"}),
    indexes = {
        @Index(name = "ix_calling_devices_user", columnList = "user_id"),
        @Index(name = "ix_calling_devices_org", columnList = "organization_id")})
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class CallingDevice extends TenantEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_name", nullable = false, length = 80)
    private String deviceName;

    /** Public contact number of the SIM. Never expose IMSI/ICCID-style identifiers. */
    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    /** e.g. ANDROID.Kept free-form so future platforms fit without migrations. */
    @Column(length = 24)
    private String platform;

    /** ONLINE / OFFLINE / BUSY / DISCONNECTED. ONLINE means heartbeat within the freshness window. */
    @Column(nullable = false, length = 16)
    private String status = "OFFLINE";

    /**
     * Base URL of the bridge app that dials THIS phone (announced via bridge heartbeat).
     * Blank = fall back to the global crm.bridge.base-url (single-bridge deployments).
     */
    @Column(name = "bridge_url", length = 500)
    private String bridgeUrl;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    private boolean isDefault;
}
