package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.CreateStaffRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.StaffRequestResponse;
import com.cityfix.backend.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffRequestControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private StaffRequestController staffRequestController;

    @Test
    @DisplayName("createStaffRequest_returnsCreatedResponse")
    void createStaffRequest_returnsCreatedResponse() {
        UUID notifId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        CreateStaffRequest request = CreateStaffRequest.builder()
                .ticketId(ticketId)
                .title("Xin cấp thêm trang thiết bị")
                .content("Cần thêm thiết bị bảo hộ")
                .build();

        StaffRequestResponse mockResponse = StaffRequestResponse.builder()
                .notificationId(notifId)
                .ticketId(ticketId)
                .title("[Yêu cầu từ cán bộ] Xin cấp thêm trang thiết bị")
                .content("Cán bộ gửi: Cán bộ\nPhòng ban: Phòng Đô thị\nNội dung: Cần thêm thiết bị bảo hộ")
                .recipientManagerIds(List.of(managerId))
                .recipientManagerNames(List.of("Trưởng phòng"))
                .createdAt(ZonedDateTime.now())
                .build();

        when(notificationService.sendStaffRequestToManager(request)).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<StaffRequestResponse>> response = staffRequestController.createStaffRequest(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getNotificationId()).isEqualTo(notifId);
        assertThat(response.getBody().getData().getRecipientManagerIds()).contains(managerId);

        verify(notificationService).sendStaffRequestToManager(request);
    }
}
