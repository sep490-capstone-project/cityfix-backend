package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class StepRequest {

    @NotNull(message = "SubCategory ID is required")
    private UUID subCategoryId;

    @NotBlank(message = "Step name is required")
    @Size(max = 150, message = "Tên bước xử lý không được vượt quá 150 ký tự")
    private String stepName;

    @Size(max = 500, message = "Mô tả bước không được vượt quá 500 ký tự")
    private String description;

    @NotNull(message = "Thời lượng xử lý không được để trống")
    @Min(value = 1, message = "Thời lượng xử lý phải ít nhất 1 giờ")
    private Integer duration;
}
