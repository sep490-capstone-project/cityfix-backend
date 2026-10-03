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
public class DuplicateTicketResponse {
    private UUID ticketId;
    private String description;
    private String address;
    private String status;
    private String categoryName;
    private Double latitude;
    private Double longitude;
    private Double distanceMeters;
    private String imageUrl;
    private LocalDateTime createdAt;
}
