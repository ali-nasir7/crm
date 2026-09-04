package com.crm.modules.meetings.repo;

import com.crm.modules.meetings.domain.Meeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID>, JpaSpecificationExecutor<Meeting> {
    Page<Meeting> findByOrganizationIdOrderByStartAtDesc(UUID organizationId, Pageable pageable);
}
