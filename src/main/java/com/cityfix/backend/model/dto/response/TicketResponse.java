package com.cityfix.backend.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TicketResponse {
    private UUID id;
    private String description;
    private String imageUrl;
    private java.util.UUID categoryId;
    private String categoryName;
    private com.cityfix.backend.model.enums.SeverityLevel severity;
    private com.cityfix.backend.model.enums.DangerLevel dangerLevel;
    private com.cityfix.backend.model.enums.LocationMethod locationMethod;
    private String address;
    private com.cityfix.backend.model.enums.TicketStatus status;
    private Double latitude;
    private Double longitude;
    private String reviewNote;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private java.util.List<TicketAssignmentResponse> assignments;
}
