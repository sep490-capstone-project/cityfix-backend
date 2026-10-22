package com.cityfix.backend.model.dto.response;

import com.cityfix.backend.model.enums.AssignmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStepResponse {
    private UUID id;
    private UUID stepId;
    private String stepName;
    private UUID assigneeId;
    private String assigneeName;
    private AssignmentStatus status;
    private java.util.List<UUID> dependsOnStepIds;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime deadline;
    private String notes;
    private java.util.List<ResolutionEvidenceResponse> evidences;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
