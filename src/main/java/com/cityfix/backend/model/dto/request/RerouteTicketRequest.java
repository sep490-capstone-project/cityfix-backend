package com.cityfix.backend.model.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @JsonProperty("reason")
    private String reason;
}
