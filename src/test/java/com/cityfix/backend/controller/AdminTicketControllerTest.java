package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.ReviewTicketRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTicketControllerTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private AdminTicketController adminTicketController;

    @Test
    @DisplayName("getInitiatedTickets_returnsPageOfTickets")
    void getInitiatedTickets_returnsPageOfTickets() {
        TicketResponse mockResponse = TicketResponse.builder().id(UUID.randomUUID()).description("Test").build();
        Page<TicketResponse> page = new PageImpl<>(List.of(mockResponse));
        
        when(ticketService.getInitiatedTickets(any(Pageable.class))).thenReturn(page);

        ResponseEntity<BaseResponse<Page<TicketResponse>>> response = adminTicketController.getInitiatedTickets(PageRequest.of(0, 20));

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

        ResponseEntity<BaseResponse<List<DepartmentResponse>>> response = adminTicketController.getSuggestedDepartments(ticketId, null);

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

        ResponseEntity<BaseResponse<TicketResponse>> response = adminTicketController.reviewTicket(ticketId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getDescription()).isEqualTo("Updated");
    }
}
