package com.cityfix.backend.model.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Thông tin deadline cho từng ticket step")
public class StepDeadlineItem {

    @NotNull(message = "ticketStepId không được để trống")
    @Schema(description = "ID của bản ghi ticket_step")
    private UUID ticketStepId;

    @NotNull(message = "Deadline không được để trống")
    @Future(message = "Deadline phải là thời gian trong tương lai")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Thời hạn hoàn thành mới", example = "2026-10-15T18:00:00")
    private LocalDateTime deadline;
}
