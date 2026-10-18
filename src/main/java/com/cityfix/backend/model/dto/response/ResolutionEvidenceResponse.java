package com.cityfix.backend.model.dto.response;

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
public class ResolutionEvidenceResponse {
    private UUID evidenceId;
    private UUID ticketStepId;
    private String imageUrl;
    private String resolutionNote;
    private String staffName;
    private LocalDateTime createdAt;
}
