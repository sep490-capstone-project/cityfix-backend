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
    private String categoryName;
    private com.cityfix.backend.model.enums.SeverityLevel severity;
    private java.util.UUID subCategoryId;
    private String subCategoryName;
    private com.cityfix.backend.model.enums.Priority priority;
    private java.math.BigDecimal confidenceScore;
    private String description;
    private Boolean isValidIssue;
}
