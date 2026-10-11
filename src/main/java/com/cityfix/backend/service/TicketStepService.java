package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.enums.AssignmentStatus;

import java.util.List;
import java.util.UUID;

public interface TicketStepService {
    TicketStepResponse updateTicketStepStatus(UUID stepId, AssignmentStatus status);

    List<TicketStepResponse> getTicketStepsByTicketId(UUID ticketId);

    void deleteTicketStep(UUID stepId);

    void cascadeCancelTicketSteps(UUID ticketId);
}
