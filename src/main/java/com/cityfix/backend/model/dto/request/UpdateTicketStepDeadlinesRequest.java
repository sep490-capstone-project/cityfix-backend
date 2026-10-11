package com.cityfix.backend.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cập nhật deadline cho nhiều ticket step của ticket")
public class UpdateTicketStepDeadlinesRequest {

    @NotEmpty(message = "Danh sách step deadlines không được để trống")
    @Valid
    @Schema(description = "Danh sách step cần cập nhật deadline")
    private List<StepDeadlineItem> stepDeadlines;
}
