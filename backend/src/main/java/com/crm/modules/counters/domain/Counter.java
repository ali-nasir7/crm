package com.crm.modules.counters.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "counters", uniqueConstraints = @UniqueConstraint(name = "uk_counters_org_key", columnNames = {"organization_id", "counter_key"}))
public class Counter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @org.hibernate.annotations.UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "counter_key", nullable = false, length = 48)
    private String key;

    @Column(nullable = false)
    private long value;
}
