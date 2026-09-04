package com.crm.modules.importx.repo;

import com.crm.modules.importx.domain.ImportRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ImportRowRepository extends JpaRepository<ImportRow, UUID> {

    Page<ImportRow> findByJobIdAndStatusOrderByRowNumberAsc(UUID jobId, String status, Pageable pageable);

    Page<ImportRow> findByJobIdOrderByRowNumberAsc(UUID jobId, Pageable pageable);

    long countByJobIdAndStatus(UUID jobId, String status);

    @Query("select r from ImportRow r where r.jobId = :jobId and r.status in ('VALID','DUPLICATE')")
    List<ImportRow> findProcessable(UUID jobId);
}
