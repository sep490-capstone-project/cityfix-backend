package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.response.NotificationResponse;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    void notifyStepStarted(TicketStep step, User staff);

    void notifyStepCompleted(TicketStep step, User staff, boolean isTicketResolved);

    Page<NotificationResponse> getMyNotifications(Boolean isRead, Pageable pageable);

    long getUnreadCount();

    NotificationResponse markAsRead(UUID notificationId);

    void markAllAsRead();
}
