package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.NotificationResponse;
import com.cityfix.backend.service.NotificationService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    @DisplayName("getMyNotifications_returnsPageOfNotifications")
    void getMyNotifications_returnsPageOfNotifications() {
        Pageable pageable = PageRequest.of(0, 20);
        NotificationResponse item = NotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .title("Tiến độ sự cố")
                .isRead(false)
                .build();
        Page<NotificationResponse> page = new PageImpl<>(List.of(item));

        when(notificationService.getMyNotifications(false, pageable)).thenReturn(page);

        ResponseEntity<BaseResponse<Page<NotificationResponse>>> response =
                notificationController.getMyNotifications(false, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getUnreadCount_returnsCount")
    void getUnreadCount_returnsCount() {
        when(notificationService.getUnreadCount()).thenReturn(5L);

        ResponseEntity<BaseResponse<Long>> response = notificationController.getUnreadCount();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(5L);
    }

    @Test
    @DisplayName("markAsRead_returnsUpdatedNotification")
    void markAsRead_returnsUpdatedNotification() {
        UUID notifId = UUID.randomUUID();
        NotificationResponse updated = NotificationResponse.builder()
                .notificationId(notifId)
                .isRead(true)
                .build();

        when(notificationService.markAsRead(notifId)).thenReturn(updated);

        ResponseEntity<BaseResponse<NotificationResponse>> response =
                notificationController.markAsRead(notifId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().isRead()).isTrue();
    }

    @Test
    @DisplayName("markAllAsRead_callsServiceAndReturnsSuccess")
    void markAllAsRead_callsServiceAndReturnsSuccess() {
        ResponseEntity<BaseResponse<Void>> response = notificationController.markAllAsRead();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(notificationService).markAllAsRead();
    }
}
