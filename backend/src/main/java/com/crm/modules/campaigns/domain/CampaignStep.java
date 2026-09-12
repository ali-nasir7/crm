package com.crm.modules.campaigns.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** One step of a sequence: send template after N days of delay relative to the previous step. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "campaign_steps", indexes = @Index(name = "ix_csteps_campaign", columnList = "campaign_id"))
public class CampaignStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @org.hibernate.annotations.UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(nullable = false)
    private int position;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "delay_days", nullable = false)
    private int delayDays;
}
