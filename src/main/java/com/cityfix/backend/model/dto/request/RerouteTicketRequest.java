package com.cityfix.backend.model.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RerouteTicketRequest {

    @NotNull(message = "ID phòng ban đích không được để trống")
    @JsonProperty("targetDepartmentId")
    private UUID targetDepartmentId;

    @NotBlank(message = "Lý do chuyển tuyến không được để trống")
    @Size(max = 1000, message = "Lý do chuyển tuyến không được vượt quá 1000 ký tự")
    @JsonProperty("reason")
    private String reason;
}
