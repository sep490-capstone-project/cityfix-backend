package com.cityfix.backend.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ResolutionEvidenceResponse {
    private UUID evidenceId;
    private String imageUrl;
    private String resolutionNote;
    private String staffName;
    private LocalDateTime createdAt;
}
