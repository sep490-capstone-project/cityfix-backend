package com.cityfix.backend.listener;

import com.cityfix.backend.event.RecurringTicketEvent;
import com.cityfix.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecurringTicketEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleRecurringTicketEvent(RecurringTicketEvent event) {
        if (event == null || event.getTicket() == null) {
            return;
        }
        try {
            log.info("Processing RecurringTicketEvent after commit for ticketId={}, occurrenceCount={}",
                    event.getTicket().getTicketId(), event.getOccurrenceCount());
            notificationService.notifyRecurringTicketAlert(
                    event.getTicket(),
                    event.getOccurrenceCount(),
                    event.getRecurringTicketIds()
            );
        } catch (Exception e) {
            log.error("Failed to send recurring ticket alert after commit for ticketId={}: {}",
                    event.getTicket().getTicketId(), e.getMessage(), e);
        }
    }
}
