package com.crm.modules.counters.repo;

import com.crm.modules.counters.domain.Counter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface CounterRepository extends JpaRepository<Counter, UUID> {

    @Modifying
    @Query(value = "insert into counters (id, organization_id, counter_key, value) values (:id, :orgId, :key, 1) " +
        "on conflict (organization_id, counter_key) do update set value = counters.value + 1 returning value", nativeQuery = true)
    long nextValue(UUID id, UUID orgId, String key);
}
