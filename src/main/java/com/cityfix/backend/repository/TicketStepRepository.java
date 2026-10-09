package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketStepRepository extends JpaRepository<TicketStep, UUID> {

    List<TicketStep> findByTicketTicketIdOrderByOrderIndexAsc(UUID ticketId);

    List<TicketStep> findByTicketTicketIdAndStatusIn(UUID ticketId, List<AssignmentStatus> statuses);

    Page<TicketStep> findByAssigneeUserIdAndStatusIn(UUID assigneeId, List<AssignmentStatus> statuses, Pageable pageable);

    Page<TicketStep> findByAssigneeDepartmentDepartmentIdAndStatusIn(UUID departmentId, List<AssignmentStatus> statuses, Pageable pageable);
}
