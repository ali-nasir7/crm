package com.crm.modules.counters.repo;

import com.crm.modules.counters.domain.Counter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface CounterRepository extends JpaRepository<Counter, UUID> {

    /**
     * Atomic upsert-and-read. Deliberately NOT @Modifying: with @Modifying Spring Data calls
     * executeUpdate(), which returns the affected ROW COUNT (always 1) instead of the RETURNING
     * value - every document number would stay "-1". As a plain native query Postgres returns
     * the `returning value` row and Spring maps it to long. Caller runs inside a transaction.
     */
    @Query(value = "insert into counters (id, organization_id, counter_key, value) values (:id, :orgId, :key, 1) " +
        "on conflict (organization_id, counter_key) do update set value = counters.value + 1 returning value", nativeQuery = true)
    long nextValue(UUID id, UUID orgId, String key);
}
