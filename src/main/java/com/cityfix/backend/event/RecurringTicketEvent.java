package com.cityfix.backend.event;

import com.cityfix.backend.model.entity.Ticket;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class RecurringTicketEvent {
    private final Ticket ticket;
    private final long occurrenceCount;
    private final Collection<UUID> recurringTicketIds;
}
