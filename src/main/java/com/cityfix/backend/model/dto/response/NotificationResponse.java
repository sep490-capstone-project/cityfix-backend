package com.cityfix.backend.model.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID notificationId;
    private UUID userId;
    private String title;
    private String content;
    private boolean isRead;
    private ZonedDateTime createdAt;
}
