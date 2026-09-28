package com.cityfix.backend.model.dto.response;

import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.TicketStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TicketHistoryResponse {
    private UUID historyId;
    private UUID ticketId;
    private UUID actorId;
    private String actorName;
    private ActionType actionType;
    private TicketStatus previousStatus;
    private TicketStatus currentStatus;
    private String actionNote;
    private LocalDateTime createdAt;
}
