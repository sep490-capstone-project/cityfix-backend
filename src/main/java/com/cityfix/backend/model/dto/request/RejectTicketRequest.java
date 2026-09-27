package com.cityfix.backend.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectTicketRequest {
    private UUID suggestedCategoryId;
    private UUID suggestedDepartmentId;
    private String reason;
}
