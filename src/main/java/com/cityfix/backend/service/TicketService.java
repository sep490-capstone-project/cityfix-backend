package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface TicketService {
    TicketResponse createTicket(CreateTicketRequest request);
    void upvoteTicket(UUID ticketId);
    List<ImageUploadResponse> uploadImage(List<MultipartFile> files, String folder);
    List<DuplicateTicketResponse> findDuplicateTickets(Double latitude, Double longitude, UUID categoryId);
    
    org.springframework.data.domain.Page<TicketResponse> getInitiatedTickets(org.springframework.data.domain.Pageable pageable);
    List<com.cityfix.backend.model.dto.response.DepartmentResponse> getSuggestedDepartments(UUID ticketId, UUID categoryId);
    TicketResponse reviewTicket(UUID ticketId, com.cityfix.backend.model.dto.request.ReviewTicketRequest request);
    TicketResponse rerouteTicket(UUID ticketId, com.cityfix.backend.model.dto.request.RerouteTicketRequest request);
    org.springframework.data.domain.Page<TicketResponse> getAssignedTicketsForStaff(com.cityfix.backend.model.enums.AssignmentStatus status, org.springframework.data.domain.Pageable pageable);
    
    TicketResponse assignTicket(UUID ticketId, com.cityfix.backend.model.dto.request.AssignTicketRequest request);

    TicketResponse forwardTicket(UUID ticketId, com.cityfix.backend.model.dto.request.RejectTicketRequest request);
    TicketResponse rejectAssignedTicket(UUID ticketId, String reason);
    TicketResponse rejectInitiatedTicket(UUID ticketId, String reason);
    TicketResponse cancelTicket(UUID ticketId);
    List<com.cityfix.backend.model.dto.response.TicketHistoryResponse> getTicketHistory(UUID ticketId);

    com.cityfix.backend.model.dto.response.TicketStepResponse updateTicketStepDeadline(UUID ticketStepId, com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlineRequest request);
    List<com.cityfix.backend.model.dto.response.TicketStepResponse> updateTicketStepDeadlines(UUID ticketId, com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlinesRequest request);
    com.cityfix.backend.model.dto.response.TicketStepResponse updateTicketStepOrderIndex(UUID ticketStepId, com.cityfix.backend.model.dto.request.UpdateTicketStepOrderRequest request);
    List<com.cityfix.backend.model.dto.response.TicketStepResponse> reorderTicketSteps(UUID ticketId, com.cityfix.backend.model.dto.request.ReorderTicketStepsRequest request);
}

