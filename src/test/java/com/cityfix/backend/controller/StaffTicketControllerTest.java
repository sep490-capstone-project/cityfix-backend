package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.TicketStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffTicketControllerTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private StaffTicketController staffTicketController;

    @Test
    @DisplayName("getAssignedTicketsForStaff_returns200SuccessWithPageData")
    void getAssignedTicketsForStaff_returns200SuccessWithPageData() {
        TicketResponse mockResponse = TicketResponse.builder()
                .id(UUID.randomUUID())
                .description("Báo cáo sự cố")
                .status(TicketStatus.ASSIGNED)
                .createdAt(LocalDateTime.now())
                .build();

        Page<TicketResponse> page = new PageImpl<>(List.of(mockResponse));

        when(ticketService.getAssignedTicketsForStaff(any(AssignmentStatus.class), any())).thenReturn(page);

        ResponseEntity<BaseResponse<Page<TicketResponse>>> response =
                staffTicketController.getAssignedTicketsForStaff(AssignmentStatus.ASSIGNED, PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Lấy danh sách vé phân công thành công.");
        assertThat(response.getBody().getData().getContent()).hasSize(1);

        verify(ticketService, times(1)).getAssignedTicketsForStaff(eq(AssignmentStatus.ASSIGNED), any());
    }

    @Test
    @DisplayName("assignTicket_returns200Success")
    void assignTicket_returns200Success() {
        UUID ticketId = UUID.randomUUID();
        com.cityfix.backend.model.dto.request.AssignTicketRequest request = new com.cityfix.backend.model.dto.request.AssignTicketRequest(UUID.randomUUID());
        
        TicketResponse mockResponse = TicketResponse.builder()
                .id(ticketId)
                .status(TicketStatus.ASSIGNED)
                .build();
                
        when(ticketService.assignTicket(eq(ticketId), any())).thenReturn(mockResponse);
        
        ResponseEntity<BaseResponse<TicketResponse>> response = staffTicketController.assignTicket(ticketId, request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        
        verify(ticketService, times(1)).assignTicket(eq(ticketId), any());
    }

    @Test
    @DisplayName("rejectTicket_returns200Success")
    void rejectTicket_returns200Success() {
        UUID ticketId = UUID.randomUUID();
        com.cityfix.backend.model.dto.request.RejectTicketRequest request = new com.cityfix.backend.model.dto.request.RejectTicketRequest(UUID.randomUUID(), null, "Sai chuyên môn");
        
        TicketResponse mockResponse = TicketResponse.builder()
                .id(ticketId)
                .status(TicketStatus.ASSIGNED)
                .build();
                
        when(ticketService.rejectTicket(eq(ticketId), any())).thenReturn(mockResponse);
        
        ResponseEntity<BaseResponse<TicketResponse>> response = staffTicketController.rejectTicket(ticketId, request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        
        verify(ticketService, times(1)).rejectTicket(eq(ticketId), any());
    }
}
