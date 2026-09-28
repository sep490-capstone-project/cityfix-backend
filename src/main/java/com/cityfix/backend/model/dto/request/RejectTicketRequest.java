package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectTicketRequest {
    private UUID suggestedCategoryId;
    private UUID suggestedDepartmentId;
    
    @NotBlank(message = "Lý do từ chối không được để trống")
    private String reason;
}
