package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class StepRequest {
    
    @NotNull(message = "SubCategory ID is required")
    private UUID subCategoryId;
    
    @NotBlank(message = "Step name is required")
    private String stepName;
    
    private String description;
    
    private Integer duration;
}
