package com.crm.modules.tasks.repo;

import com.crm.modules.tasks.domain.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    Page<Task> findByOrganizationIdAndAssignedUserIdOrderByDueAtAsc(UUID orgId, UUID userId, Pageable pageable);

    long countByOrganizationIdAndAssignedUserIdAndStatusAndDueAtBefore(UUID orgId, UUID userId, String status, Instant due);

    long countByOrganizationIdAndAssignedUserIdAndStatusAndDueAtBetween(UUID orgId, UUID userId, String status, Instant from, Instant to);

    @Query("select t from Task t where t.status = 'OPEN' and t.dueAt < :now")
    List<Task> findOverdue(Instant now, Pageable pageable);
}
