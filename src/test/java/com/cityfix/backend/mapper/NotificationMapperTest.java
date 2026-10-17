package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.NotificationResponse;
import com.cityfix.backend.model.entity.Notification;
import com.cityfix.backend.model.entity.User;
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
        ZonedDateTime now = ZonedDateTime.now();

        User user = User.builder().userId(userId).build();

        Notification unread = Notification.builder()
                .notificationId(notifId)
                .user(user)
                .title("Tiêu đề")
                .content("Nội dung")
                .isRead(false)
                .createdAt(now)
                .build();

        NotificationResponse unreadResp = notificationMapper.toResponse(unread);

        assertThat(unreadResp).isNotNull();
        assertThat(unreadResp.getNotificationId()).isEqualTo(notifId);
        assertThat(unreadResp.getUserId()).isEqualTo(userId);
        assertThat(unreadResp.getTitle()).isEqualTo("Tiêu đề");
        assertThat(unreadResp.getContent()).isEqualTo("Nội dung");
        assertThat(unreadResp.isRead()).isFalse();
        assertThat(unreadResp.getCreatedAt()).isEqualTo(now);

        unread.setRead(true);
        NotificationResponse readResp = notificationMapper.toResponse(unread);

        assertThat(readResp).isNotNull();
        assertThat(readResp.isRead()).isTrue();
    }

    @Test
    @DisplayName("toResponse_whenNull_returnsNull")
    void toResponse_whenNull_returnsNull() {
        assertThat(notificationMapper.toResponse(null)).isNull();
    }
}
