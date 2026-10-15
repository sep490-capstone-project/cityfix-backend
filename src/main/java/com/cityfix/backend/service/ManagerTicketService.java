package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.*;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ManagerTicketService {

    Page<TicketResponse> getInitiatedTickets(Pageable pageable);

    Page<TicketResponse> getDepartmentTickets(
            UUID departmentId,
            com.cityfix.backend.model.enums.SeverityLevel severity,
            com.cityfix.backend.model.enums.Priority priority,
            com.cityfix.backend.model.enums.TicketStatus status,
            Pageable pageable
    );

    List<DepartmentResponse> getSuggestedDepartments(UUID ticketId, UUID categoryId);

    TicketResponse reviewTicket(UUID ticketId, ReviewTicketRequest request);

    TicketResponse rejectInitiatedTicket(UUID ticketId, String reason);

    TicketStepResponse updateTicketStepDeadline(UUID ticketStepId, UpdateTicketStepDeadlineRequest request);

    List<TicketStepResponse> updateTicketStepDeadlines(UUID ticketId, UpdateTicketStepDeadlinesRequest request);

    TicketStepResponse updateTicketStepOrderIndex(UUID ticketStepId, UpdateTicketStepOrderRequest request);

    List<TicketStepResponse> reorderTicketSteps(UUID ticketId, ReorderTicketStepsRequest request);

    TicketResponse updateTicketDuration(UUID ticketId, UpdateTicketDurationRequest request);
}
