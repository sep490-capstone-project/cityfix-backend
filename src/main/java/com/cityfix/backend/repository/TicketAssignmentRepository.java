package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.TicketAssignment;
import com.cityfix.backend.model.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketAssignmentRepository extends JpaRepository<TicketAssignment, UUID> {

    Page<TicketAssignment> findByDepartmentDepartmentIdAndStatusIn(UUID departmentId, List<AssignmentStatus> statuses, Pageable pageable);

    List<TicketAssignment> findByTicketTicketIdAndStatusIn(UUID ticketId, List<AssignmentStatus> statuses);

    Optional<TicketAssignment> findByTicketTicketIdAndDepartmentDepartmentId(UUID ticketId, UUID departmentId);
}
