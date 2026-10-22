package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.*;
import com.cityfix.backend.model.dto.response.SuggestedAssigneeResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ManagerTicketService {

    Page<TicketResponse> getInitiatedTickets(Pageable pageable);

    Page<TicketResponse> getManagerAssignedDepartmentTickets(UUID departmentId, Pageable pageable);

    Page<TicketResponse> getDepartmentTickets(
            UUID departmentId,

            com.cityfix.backend.model.enums.Priority priority,
            com.cityfix.backend.model.enums.TicketStatus status,
            Pageable pageable
    );

    List<com.cityfix.backend.model.dto.response.ManagerDepartmentResponse> getManagerDepartments();

    TicketResponse reviewTicket(UUID ticketId, ReviewTicketRequest request);

    TicketResponse rejectInitiatedTicket(UUID ticketId, String reason);

    TicketStepResponse updateTicketStepDeadline(UUID ticketStepId, UpdateTicketStepDeadlineRequest request);

    List<TicketStepResponse> updateTicketStepDeadlines(UUID ticketId, UpdateTicketStepDeadlinesRequest request);

    TicketStepResponse updateTicketStepDependencies(UUID ticketStepId, UpdateTicketStepDependenciesRequest request);

    @Deprecated(since = "v2.0", forRemoval = false)
    TicketStepResponse updateTicketStepOrderIndex(UUID ticketStepId, UpdateTicketStepOrderRequest request);

    @Deprecated(since = "v2.0", forRemoval = false)
    List<TicketStepResponse> reorderTicketSteps(UUID ticketId, ReorderTicketStepsRequest request);

    Page<TicketResponse> getResolvedTickets(Pageable pageable);

    TicketStepResponse updateResolvedTicketStepStatus(
            UUID stepId,
            com.cityfix.backend.model.enums.AssignmentStatus targetStatus
    );
}
