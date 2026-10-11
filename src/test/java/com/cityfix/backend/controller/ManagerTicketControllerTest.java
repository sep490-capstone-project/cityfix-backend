package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.*;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.service.TicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerTicketControllerTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private ManagerTicketController managerTicketController;

    @Test
    @DisplayName("getInitiatedTickets_returnsPageOfTickets")
    void getInitiatedTickets_returnsPageOfTickets() {
        TicketResponse mockResponse = TicketResponse.builder().id(UUID.randomUUID()).description("Test").build();
        Page<TicketResponse> page = new PageImpl<>(List.of(mockResponse));
        
        when(ticketService.getInitiatedTickets(any(Pageable.class))).thenReturn(page);

        ResponseEntity<BaseResponse<Page<TicketResponse>>> response = managerTicketController.getInitiatedTickets(PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getSuggestedDepartments_returnsListOfDepartments")
    void getSuggestedDepartments_returnsListOfDepartments() {
        UUID ticketId = UUID.randomUUID();
        DepartmentResponse mockResponse = new DepartmentResponse(UUID.randomUUID(), "Test Dept", null, null, null, 0, true);
        
        when(ticketService.getSuggestedDepartments(eq(ticketId), any())).thenReturn(List.of(mockResponse));

        ResponseEntity<BaseResponse<List<DepartmentResponse>>> response = managerTicketController.getSuggestedDepartments(ticketId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).name()).isEqualTo("Test Dept");
    }

    @Test
    @DisplayName("reviewTicket_validRequest_returnsUpdatedTicket")
    void reviewTicket_validRequest_returnsUpdatedTicket() {
        UUID ticketId = UUID.randomUUID();
        ReviewTicketRequest request = new ReviewTicketRequest();
        request.setDepartmentIds(List.of(UUID.randomUUID()));
        request.setIsLifeThreatening(true);

        TicketResponse mockResponse = TicketResponse.builder().id(ticketId).description("Updated").build();
        
        when(ticketService.reviewTicket(eq(ticketId), any(ReviewTicketRequest.class))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<TicketResponse>> response = managerTicketController.reviewTicket(ticketId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getDescription()).isEqualTo("Updated");
    }

    @Test
    @DisplayName("rejectInitiatedTicket_validRequest_returnsUpdatedTicket")
    void rejectInitiatedTicket_validRequest_returnsUpdatedTicket() {
        UUID ticketId = UUID.randomUUID();
        RejectTicketRequest request = new RejectTicketRequest();
        request.setReason("Invalid report");

        TicketResponse mockResponse = TicketResponse.builder().id(ticketId).description("Rejected").build();

        when(ticketService.rejectInitiatedTicket(eq(ticketId), eq("Invalid report"))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<TicketResponse>> response = managerTicketController.rejectInitiatedTicket(ticketId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("updateTicketStepDeadline_validRequest_returnsUpdatedStep")
    void updateTicketStepDeadline_validRequest_returnsUpdatedStep() {
        UUID stepId = UUID.randomUUID();
        LocalDateTime deadline = LocalDateTime.now().plusDays(3);
        UpdateTicketStepDeadlineRequest request = UpdateTicketStepDeadlineRequest.builder()
                .deadline(deadline)
                .build();

        TicketStepResponse mockResponse = TicketStepResponse.builder()
                .id(stepId)
                .deadline(deadline)
                .build();

        when(ticketService.updateTicketStepDeadline(eq(stepId), any(UpdateTicketStepDeadlineRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<BaseResponse<TicketStepResponse>> response = managerTicketController.updateTicketStepDeadline(stepId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getDeadline()).isEqualTo(deadline);
    }

    @Test
    @DisplayName("updateTicketStepDeadlines_validRequest_returnsUpdatedSteps")
    void updateTicketStepDeadlines_validRequest_returnsUpdatedSteps() {
        UUID ticketId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        LocalDateTime deadline = LocalDateTime.now().plusDays(2);
        UpdateTicketStepDeadlinesRequest request = UpdateTicketStepDeadlinesRequest.builder()
                .stepDeadlines(List.of(new StepDeadlineItem(stepId, deadline)))
                .build();

        TicketStepResponse mockStep = TicketStepResponse.builder().id(stepId).deadline(deadline).build();

        when(ticketService.updateTicketStepDeadlines(eq(ticketId), any(UpdateTicketStepDeadlinesRequest.class)))
                .thenReturn(List.of(mockStep));

        ResponseEntity<BaseResponse<List<TicketStepResponse>>> response = managerTicketController.updateTicketStepDeadlines(ticketId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("updateTicketStepOrderIndex_validRequest_returnsUpdatedStep")
    void updateTicketStepOrderIndex_validRequest_returnsUpdatedStep() {
        UUID stepId = UUID.randomUUID();
        UpdateTicketStepOrderRequest request = UpdateTicketStepOrderRequest.builder()
                .orderIndex(3)
                .build();

        TicketStepResponse mockResponse = TicketStepResponse.builder()
                .id(stepId)
                .orderIndex(3)
                .build();

        when(ticketService.updateTicketStepOrderIndex(eq(stepId), any(UpdateTicketStepOrderRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<BaseResponse<TicketStepResponse>> response = managerTicketController.updateTicketStepOrderIndex(stepId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getOrderIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("reorderTicketSteps_validRequest_returnsUpdatedSteps")
    void reorderTicketSteps_validRequest_returnsUpdatedSteps() {
        UUID ticketId = UUID.randomUUID();
        UUID stepId1 = UUID.randomUUID();
        UUID stepId2 = UUID.randomUUID();
        ReorderTicketStepsRequest request = ReorderTicketStepsRequest.builder()
                .stepOrders(List.of(new StepOrderItem(stepId1, 1), new StepOrderItem(stepId2, 0)))
                .build();

        TicketStepResponse mock1 = TicketStepResponse.builder().id(stepId1).orderIndex(1).build();
        TicketStepResponse mock2 = TicketStepResponse.builder().id(stepId2).orderIndex(0).build();

        when(ticketService.reorderTicketSteps(eq(ticketId), any(ReorderTicketStepsRequest.class)))
                .thenReturn(List.of(mock2, mock1));

        ResponseEntity<BaseResponse<List<TicketStepResponse>>> response = managerTicketController.reorderTicketSteps(ticketId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(2);
    }
}
