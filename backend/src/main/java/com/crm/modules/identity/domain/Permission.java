package com.crm.modules.identity.domain;

import com.crm.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Global permission catalogue (shared across organizations); roles reference these keys. */
@Getter
@Setter
@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(length = 32)
    private String category;
}
