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
@Schema(description = "Yêu cầu sắp xếp lại thứ tự (order_index) các ticket step của ticket")
public class ReorderTicketStepsRequest {

    @NotEmpty(message = "Danh sách thứ tự bước không được để trống")
    @Valid
    @Schema(description = "Danh sách ticket step và thứ tự thực hiện mới")
    private List<StepOrderItem> stepOrders;
}
