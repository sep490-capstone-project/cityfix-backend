package com.cityfix.backend.listener;

import com.cityfix.backend.event.RecurringTicketEvent;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringTicketEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RecurringTicketEventListener eventListener;

    @Test
    @DisplayName("handleRecurringTicketEvent_validEvent_callsNotificationService")
    void handleRecurringTicketEvent_validEvent_callsNotificationService() {
        Ticket ticket = Ticket.builder().ticketId(UUID.randomUUID()).build();
        List<UUID> oldTicketIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        RecurringTicketEvent event = new RecurringTicketEvent(ticket, 3L, oldTicketIds);

        eventListener.handleRecurringTicketEvent(event);

        verify(notificationService, times(1)).notifyRecurringTicketAlert(ticket, 3L, oldTicketIds);
    }

    @Test
    @DisplayName("handleRecurringTicketEvent_nullEventOrTicket_doesNothing")
    void handleRecurringTicketEvent_nullEventOrTicket_doesNothing() {
        eventListener.handleRecurringTicketEvent(null);
        eventListener.handleRecurringTicketEvent(new RecurringTicketEvent(null, 3L, List.of()));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("handleRecurringTicketEvent_notificationThrowsException_catchesAndLogsWithoutThrowing")
    void handleRecurringTicketEvent_notificationThrowsException_catchesAndLogsWithoutThrowing() {
        Ticket ticket = Ticket.builder().ticketId(UUID.randomUUID()).build();
        RecurringTicketEvent event = new RecurringTicketEvent(ticket, 3L, List.of());

        doThrow(new RuntimeException("WebSocket server down"))
                .when(notificationService).notifyRecurringTicketAlert(any(), anyLong(), any());

        // Should not throw exception
        eventListener.handleRecurringTicketEvent(event);

        verify(notificationService, times(1)).notifyRecurringTicketAlert(any(), anyLong(), any());
    }
}
