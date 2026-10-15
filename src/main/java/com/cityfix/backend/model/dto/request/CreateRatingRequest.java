package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRatingRequest {

    @NotNull(message = "Đánh giá sao không được để trống")
    @Min(value = 1, message = "Đánh giá sao tối thiểu là 1")
    @Max(value = 5, message = "Đánh giá sao tối đa là 5")
    private Integer rate;

    @Builder.Default
    private Boolean isAnonymous = false;

    @Size(max = 1000, message = "Phản hồi không được vượt quá 1000 ký tự")
    private String feedback;
}
