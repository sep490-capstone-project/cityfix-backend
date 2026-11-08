package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.CreateStaffRequest;
import com.cityfix.backend.model.dto.response.NotificationResponse;
import com.cityfix.backend.model.dto.response.StaffRequestResponse;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.UUID;

public interface NotificationService {

    void createAndSendNotification(Ticket ticket, Collection<User> recipients, String title, String content);

    void createAndSendNotification(Collection<User> recipients, String title, String content);

    void notifyStepStarted(TicketStep step, User staff);

    void notifyStepCompleted(TicketStep step, User staff, boolean isTicketResolved);
    
    void notifyRecurringTicketAlert(Ticket ticket, long occurrenceCount, Collection<UUID> recurringTicketIds);
    
    default void notifyRecurringTicketAlert(Ticket ticket, long occurrenceCount) {
        notifyRecurringTicketAlert(ticket, occurrenceCount, java.util.Collections.emptyList());
    }

    void notifySosTicketStatusChanged(Ticket ticket, TicketStatus prevStatus, TicketStatus newStatus);

    StaffRequestResponse sendStaffRequestToManager(CreateStaffRequest request);

    Page<NotificationResponse> getMyNotifications(Boolean isRead, Pageable pageable);

    long getUnreadCount();

    NotificationResponse markAsRead(UUID notificationId);

    void markAllAsRead();
}
