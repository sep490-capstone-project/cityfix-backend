package com.cityfix.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalyzeResponse {
    private java.util.UUID categoryId;
    private com.cityfix.backend.model.enums.SeverityLevel severity;
    private com.cityfix.backend.model.enums.DangerLevel dangerLevel;
    private java.math.BigDecimal confidenceScore;
    private String description;
    private Boolean isValidIssue;
}
