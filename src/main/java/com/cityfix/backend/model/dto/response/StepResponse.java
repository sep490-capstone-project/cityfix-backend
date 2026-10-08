package com.cityfix.backend.model.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class StepResponse {
    private UUID stepId;
    private UUID subCategoryId;
    private String stepName;
    private String description;
    private Integer duration;
}
