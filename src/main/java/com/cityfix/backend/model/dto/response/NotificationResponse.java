package com.cityfix.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID notificationId;
    private UUID userId;
    private UUID ticketId;
    private String title;
    private String content;
    private boolean isRead;
    private ZonedDateTime readAt;
    private ZonedDateTime createdAt;
}
