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
public class RatingResponse {
    private UUID id;
    private UUID ticketId;
    private UUID userId;
    private String userName;
    private String userAvatar;
    private Integer rate;
    private Boolean isAnonymous;
    private String feedback;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
