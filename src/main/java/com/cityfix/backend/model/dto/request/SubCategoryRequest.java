package com.cityfix.backend.model.dto.request;

import com.cityfix.backend.model.enums.Priority;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class SubCategoryRequest {

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Tên sự cố phụ không được vượt quá 150 ký tự")
    private String name;

    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;

    @Min(value = 0, message = "SLA không được âm")
    private Integer sla;

    @NotNull(message = "Priority is required")
    private Priority priority;

    @NotNull(message = "Is active is required")
    private Boolean isActive;
}
