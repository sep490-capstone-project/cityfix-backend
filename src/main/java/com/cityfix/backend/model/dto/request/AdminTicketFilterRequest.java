package com.cityfix.backend.model.dto.request;

import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.TicketStatus;
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
public class AdminTicketFilterRequest {
    private String keyword;
    private TicketStatus status;
    private Priority priority;
    private UUID categoryId;
    private UUID subCategoryId;
    private UUID departmentId;
    private Boolean unassigned;
    private String province;
    private String ward;
    private Boolean isLifeThreatening;
    private Boolean needsManualReview;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
}
