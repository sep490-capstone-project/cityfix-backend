package com.cityfix.backend.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin thứ tự (order_index) cho từng ticket step")
public class StepOrderItem {

    @NotNull(message = "ticketStepId không được để trống")
    @Schema(description = "ID bản ghi ticket_step")
    private UUID ticketStepId;

    @NotNull(message = "orderIndex không được để trống")
    @Min(value = 0, message = "orderIndex phải lớn hơn hoặc bằng 0")
    @Schema(description = "Thứ tự thực hiện mới (bắt đầu từ 0)", example = "0")
    private Integer orderIndex;
}
