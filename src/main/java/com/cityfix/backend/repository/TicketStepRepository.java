package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TicketStepRepository extends JpaRepository<TicketStep, UUID> {

    @Override
    @EntityGraph(attributePaths = {"dependsOn", "step", "assignee"})
    java.util.Optional<TicketStep> findById(UUID id);

    @EntityGraph(attributePaths = {"dependsOn", "step", "assignee"})
    List<TicketStep> findByTicketTicketId(UUID ticketId);

    @EntityGraph(attributePaths = {"dependsOn", "step", "assignee"})
    List<TicketStep> findByTicket_TicketId(UUID ticketId);

    java.util.Optional<TicketStep> findByTicketTicketIdAndStepStepId(UUID ticketId, UUID stepId);

    @EntityGraph(attributePaths = {"dependsOn", "step", "assignee"})
    List<TicketStep> findByTicket_TicketIdOrderByCreatedAtAscIdAsc(UUID ticketId);

    @EntityGraph(attributePaths = {"dependsOn", "step", "assignee"})
    List<TicketStep> findByTicketTicketIdOrderByCreatedAtAscIdAsc(UUID ticketId);

    List<TicketStep> findByTicketTicketIdAndStatusInOrderByCreatedAtAscIdAsc(UUID ticketId, List<AssignmentStatus> statuses);

    List<TicketStep> findByTicketTicketIdOrderByCreatedAtAsc(UUID ticketId);

    List<TicketStep> findByTicketTicketIdAndStatusIn(UUID ticketId, List<AssignmentStatus> statuses);

    List<TicketStep> findByTicketTicketIdAndStatusInOrderByCreatedAtAsc(UUID ticketId, List<AssignmentStatus> statuses);

    Page<TicketStep> findByAssigneeUserIdAndStatusIn(UUID assigneeId, List<AssignmentStatus> statuses, Pageable pageable);

    Page<TicketStep> findByAssigneeDepartmentDepartmentIdAndStatusIn(UUID departmentId, List<AssignmentStatus> statuses, Pageable pageable);

    @Query("SELECT DISTINCT ts.ticket FROM TicketStep ts WHERE ts.assignee.department.departmentId = :departmentId AND ts.status IN :statuses")
    Page<Ticket> findDistinctTicketsByDepartmentAndStatusIn(@Param("departmentId") UUID departmentId, @Param("statuses") List<AssignmentStatus> statuses, Pageable pageable);

    @Query("SELECT DISTINCT ts.ticket FROM TicketStep ts WHERE ts.assignee.userId = :staffId AND ts.status IN :statuses")
    Page<Ticket> findDistinctTicketsByAssigneeUserIdAndStatusIn(@Param("staffId") UUID staffId, @Param("statuses") List<AssignmentStatus> statuses, Pageable pageable);

    @Query("SELECT DISTINCT ts.ticket FROM TicketStep ts WHERE ts.assignee.userId = :staffId AND ts.isDeleted = false")
    Page<Ticket> findDistinctTicketsByAssigneeUserId(@Param("staffId") UUID staffId, Pageable pageable);

    @Query("SELECT ts FROM TicketStep ts " +
           "LEFT JOIN FETCH ts.assignee a " +
           "LEFT JOIN FETCH a.department d " +
           "WHERE ts.id = :stepId")
    java.util.Optional<TicketStep> findByIdWithAssigneeAndDepartment(@Param("stepId") UUID stepId);
}
