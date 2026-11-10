package com.cityfix.backend.controller;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.response.AdminAnalyticsResponse;
import com.cityfix.backend.model.dto.response.AdminDashboardSummaryResponse;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.service.AdminDashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock
    private AdminDashboardService adminDashboardService;

    @InjectMocks
    private AdminDashboardController adminDashboardController;

    @Test
    @DisplayName("getDashboardSummary_returns200OK")
    void getDashboardSummary_returns200OK() {
        AdminDashboardSummaryResponse mockSummary = AdminDashboardSummaryResponse.builder()
                .ticketSummary(AdminDashboardSummaryResponse.TicketKpiSummary.builder()
                        .totalTickets(50L)
                        .overallResolutionRate(80.0)
                        .build())
                .build();

        when(adminDashboardService.getDashboardSummary()).thenReturn(mockSummary);

        ResponseEntity<BaseResponse<AdminDashboardSummaryResponse>> response =
                adminDashboardController.getDashboardSummary();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getTicketSummary().getTotalTickets()).isEqualTo(50L);
    }

    @Test
    @DisplayName("getAnalytics_jsonFormat_returnsBaseResponseWithAnalyticsAndFullEndOfDayToDate")
    @SuppressWarnings("unchecked")
    void getAnalytics_jsonFormat_returnsBaseResponseWithAnalytics() {
        AdminAnalyticsResponse mockAnalytics = AdminAnalyticsResponse.builder()
                .overview(AdminAnalyticsResponse.AnalyticsOverview.builder()
                        .totalTickets(25L)
                        .resolutionRate(75.0)
                        .build())
                .build();

        when(adminDashboardService.getAnalytics(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAnalytics);

        ResponseEntity<?> response = adminDashboardController.getAnalytics(
                "2026-11-01", "2026-11-10", "DAY", "TP. Hồ Chí Minh", null, null, null, "json"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(BaseResponse.class);
        BaseResponse<AdminAnalyticsResponse> body = (BaseResponse<AdminAnalyticsResponse>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData().getOverview().getTotalTickets()).isEqualTo(25L);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(adminDashboardService).getAnalytics(
                fromCaptor.capture(), toCaptor.capture(), any(), any(), any(), any(), any()
        );
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(2026, 11, 1).atStartOfDay());
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.of(2026, 11, 10).atTime(LocalTime.MAX));
    }

    @Test
    @DisplayName("getAnalytics_csvFormat_returnsCsvAttachment")
    void getAnalytics_csvFormat_returnsCsvAttachment() {
        byte[] csvData = "\uFEFFChi so,Gia tri\nTong so ho so,10\n".getBytes(StandardCharsets.UTF_8);
        when(adminDashboardService.exportAnalyticsCsv(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(csvData);

        ResponseEntity<?> response = adminDashboardController.getAnalytics(
                "2026-11-01", "2026-11-10", "DAY", null, null, null, null, "csv"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("cityfix_analytics_report.csv");
        assertThat(response.getBody()).isInstanceOf(byte[].class);
    }

    @Test
    @DisplayName("exportAnalyticsCsv_invalidDateFormat_throwsInvalidRequest")
    void exportAnalyticsCsv_invalidDateFormat_throwsInvalidRequest() {
        assertThatThrownBy(() -> adminDashboardController.exportAnalyticsCsv(
                "not-a-date", null, "DAY", null, null, null, null
        ))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
