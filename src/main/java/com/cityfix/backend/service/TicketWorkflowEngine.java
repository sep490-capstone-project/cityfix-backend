package com.cityfix.backend.service;

import java.util.UUID;

public interface TicketWorkflowEngine {
    void handleStepCompletion(UUID stepId);
}
