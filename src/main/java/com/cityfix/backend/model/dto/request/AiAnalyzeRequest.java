package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiAnalyzeRequest {
    
    @NotBlank(message = "Image URL is required")
    private String imageUrl;
}
