package com.cityfix.backend.controller;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.request.AdminTicketFilterRequest;
import com.cityfix.backend.model.dto.response.AdminTicketDetailResponse;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.service.AdminTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTicketControllerTest {

    @Mock
    private AdminTicketService adminTicketService;

    @InjectMocks
    private AdminTicketController adminTicketController;

    private UUID ticketId;
    private TicketResponse sampleTicketResponse;
    private AdminTicketDetailResponse sampleDetailResponse;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        sampleTicketResponse = TicketResponse.builder()
                .id(ticketId)
                .description("Sự cố đèn đường")
                .status(TicketStatus.IN_PROGRESS)
                .priority(Priority.MEDIUM)
                .build();

        sampleDetailResponse = AdminTicketDetailResponse.builder()
                .id(ticketId)
                .description("Sự cố đèn đường")
                .status(TicketStatus.IN_PROGRESS)
                .priority(Priority.MEDIUM)
                .upvoteCount(5L)
                .build();
    }

    @Test
    @DisplayName("getAllTickets_validFilterParams_returns200OK")
    void getAllTickets_validFilterParams_returns200OK() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<TicketResponse> page = new PageImpl<>(List.of(sampleTicketResponse), pageable, 1);

        when(adminTicketService.getAllTickets(any(AdminTicketFilterRequest.class), eq(pageable)))
                .thenReturn(page);

        ResponseEntity<BaseResponse<Page<TicketResponse>>> response = adminTicketController.getAllTickets(
                "đèn đường",
                TicketStatus.IN_PROGRESS,
                Priority.MEDIUM,
                null,
                null,
                null,
                false,
                "TP. Hồ Chí Minh",
                "Phường Bến Nghé",
                false,
                false,
                "2026-11-01",
                "2026-11-10",
                pageable
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getTotalElements()).isEqualTo(1);

        ArgumentCaptor<AdminTicketFilterRequest> captor = ArgumentCaptor.forClass(AdminTicketFilterRequest.class);
        verify(adminTicketService).getAllTickets(captor.capture(), eq(pageable));
        AdminTicketFilterRequest captured = captor.getValue();
        assertThat(captured.getKeyword()).isEqualTo("đèn đường");
        assertThat(captured.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(captured.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(captured.getUnassigned()).isFalse();
        assertThat(captured.getFromDate()).isNotNull();
        assertThat(captured.getToDate()).isNotNull();
    }

    @Test
    @DisplayName("getAllTickets_fromDateAfterToDate_throwsInvalidRequest")
    void getAllTickets_fromDateAfterToDate_throwsInvalidRequest() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> adminTicketController.getAllTickets(
                null, null, null, null, null, null, null, null, null, null, null,
                "2026-11-15", "2026-11-01", pageable
        ))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("getTicketDetail_validId_returns200OK")
    void getTicketDetail_validId_returns200OK() {
        when(adminTicketService.getTicketDetail(ticketId)).thenReturn(sampleDetailResponse);

        ResponseEntity<BaseResponse<AdminTicketDetailResponse>> response =
                adminTicketController.getTicketDetail(ticketId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getId()).isEqualTo(ticketId);
        assertThat(response.getBody().getData().getUpvoteCount()).isEqualTo(5L);
    }
}
