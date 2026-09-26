package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ReviewTicketRequest {
    private UUID categoryId;
    
    @NotEmpty(message = "Danh sách phòng ban không được để trống")
    private List<UUID> departmentIds;
    
    private Boolean isLifeThreatening;
    
    private String reviewNote;
}
