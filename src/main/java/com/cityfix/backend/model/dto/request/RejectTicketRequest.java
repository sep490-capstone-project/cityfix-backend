package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectTicketRequest {
    @NotBlank(message = "Lý do từ chối không được để trống")
    @Size(max = 1000, message = "Lý do từ chối không được vượt quá 1000 ký tự")
    private String reason;
}
