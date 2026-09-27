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
}
