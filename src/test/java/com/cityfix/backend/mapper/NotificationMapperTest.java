package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.NotificationResponse;
import com.cityfix.backend.model.entity.Notification;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.UserNotification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMapperTest {

    private final NotificationMapper notificationMapper = Mappers.getMapper(NotificationMapper.class);

    @Test
    @DisplayName("toResponse_mapsAllFieldsIncludingIsReadFalseAndTrue")
    void toResponse_mapsAllFieldsIncludingIsReadFalseAndTrue() {
        UUID notifId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        ZonedDateTime now = ZonedDateTime.now();

        User user = User.builder().userId(userId).build();
        Ticket ticket = Ticket.builder().ticketId(ticketId).build();

        Notification notif = Notification.builder()
                .notificationId(notifId)
                .ticket(ticket)
                .title("Tiêu đề")
                .content("Nội dung")
                .createdAt(now)
                .build();

        UserNotification unread = UserNotification.builder()
                .id(UUID.randomUUID())
                .notification(notif)
                .user(user)
                .isRead(false)
                .createdAt(now)
                .build();

        NotificationResponse unreadResp = notificationMapper.toResponse(unread);

        assertThat(unreadResp).isNotNull();
        assertThat(unreadResp.getNotificationId()).isEqualTo(notifId);
        assertThat(unreadResp.getUserId()).isEqualTo(userId);
        assertThat(unreadResp.getTicketId()).isEqualTo(ticketId);
        assertThat(unreadResp.getTitle()).isEqualTo("Tiêu đề");
        assertThat(unreadResp.getContent()).isEqualTo("Nội dung");
        assertThat(unreadResp.isRead()).isFalse();
        assertThat(unreadResp.getCreatedAt()).isEqualTo(now);

        unread.setRead(true);
        unread.setReadAt(now.plusMinutes(5));
        NotificationResponse readResp = notificationMapper.toResponse(unread);

        assertThat(readResp).isNotNull();
        assertThat(readResp.isRead()).isTrue();
        assertThat(readResp.getReadAt()).isEqualTo(now.plusMinutes(5));
    }

    @Test
    @DisplayName("toResponse_whenNotificationHasNoTicket_mapsSuccessfullyWithNullTicketId")
    void toResponse_whenNotificationHasNoTicket_mapsSuccessfullyWithNullTicketId() {
        UUID notifId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ZonedDateTime now = ZonedDateTime.now();

        User user = User.builder().userId(userId).build();

        Notification notifWithoutTicket = Notification.builder()
                .notificationId(notifId)
                .ticket(null) // System / General notification
                .title("Thông báo bảo trì hệ thống")
                .content("Hệ thống bảo trì từ 0h - 2h sáng.")
                .createdAt(now)
                .build();

        UserNotification un = UserNotification.builder()
                .id(UUID.randomUUID())
                .notification(notifWithoutTicket)
                .user(user)
                .isRead(false)
                .createdAt(now)
                .build();

        NotificationResponse resp = notificationMapper.toResponse(un);

        assertThat(resp).isNotNull();
        assertThat(resp.getNotificationId()).isEqualTo(notifId);
        assertThat(resp.getTicketId()).isNull();
        assertThat(resp.getTitle()).isEqualTo("Thông báo bảo trì hệ thống");
        assertThat(resp.getContent()).isEqualTo("Hệ thống bảo trì từ 0h - 2h sáng.");
    }

    @Test
    @DisplayName("toResponse_whenInnerNotificationIsNull_doesNotThrowNPE")
    void toResponse_whenInnerNotificationIsNull_doesNotThrowNPE() {
        UserNotification un = UserNotification.builder()
                .id(UUID.randomUUID())
                .notification(null)
                .user(null)
                .isRead(false)
                .build();

        NotificationResponse resp = notificationMapper.toResponse(un);

        assertThat(resp).isNotNull();
        assertThat(resp.getNotificationId()).isNull();
        assertThat(resp.getTicketId()).isNull();
    }

    @Test
    @DisplayName("toResponse_whenNull_returnsNull")
    void toResponse_whenNull_returnsNull() {
        assertThat(notificationMapper.toResponse(null)).isNull();
    }
}
