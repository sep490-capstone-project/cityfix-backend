package com.cityfix.backend.model.dto.response;

import com.cityfix.backend.model.enums.AssignmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TicketAssignmentResponse {
    private UUID assignmentId;
    private UUID departmentId;
    private String departmentName;
    private UUID assigneeId;
    private String assigneeName;
    private AssignmentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
