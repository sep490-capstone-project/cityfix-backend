package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiAnalyzeRequest {
    
    @NotBlank(message = "Media URL is required")
    private String mediaUrl;
}
