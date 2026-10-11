package com.cityfix.backend.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request cập nhật thời lượng xử lý vé (Ticket duration)")
public class UpdateTicketDurationRequest {

    @NotNull(message = "Duration is required")
    @Positive(message = "Duration must be greater than 0")
    @Schema(description = "Thời lượng xử lý tính theo giờ (Hours)", example = "48")
    private Integer duration;
}
