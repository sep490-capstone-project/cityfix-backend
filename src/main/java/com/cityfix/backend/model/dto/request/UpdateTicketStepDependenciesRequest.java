package com.cityfix.backend.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cập nhật danh sách các bước phụ thuộc (dependencies) cho bước xử lý")
public class UpdateTicketStepDependenciesRequest {

    @NotNull(message = "Danh sách bước phụ thuộc không được để null")
    @Schema(
            description = "Danh sách ID (ticket_step.id) của các bước tiên quyết cần hoàn thành trước bước này",
            example = "[\"b451d8b9-4458-45e0-94d0-f1c5d4ef2891\", \"c123d456-7890-4abc-def1-234567890abc\"]"
    )
    private List<UUID> dependsOnStepIds;
}
