package com.cityfix.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedAssigneeResponse {
    private UUID assigneeId;
    private String assigneeName;
    private String assigneePhone;
    private String assigneeEmail;
    private UUID departmentId;
    private String departmentName;
}
