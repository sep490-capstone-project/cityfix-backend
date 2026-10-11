package com.cityfix.backend.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cập nhật thứ tự thực hiện (order_index) cho một ticket step")
public class UpdateTicketStepOrderRequest {

    @NotNull(message = "orderIndex không được để trống")
    @Min(value = 0, message = "orderIndex phải lớn hơn hoặc bằng 0")
    @Schema(description = "Thứ tự thực hiện mới của step trong ticket_step", example = "1")
    private Integer orderIndex;
}
