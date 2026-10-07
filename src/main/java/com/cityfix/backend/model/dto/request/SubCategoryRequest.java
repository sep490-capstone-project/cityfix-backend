package com.cityfix.backend.model.dto.request;

import com.cityfix.backend.model.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SubCategoryRequest {

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private Integer sla;

    @NotNull(message = "Priority is required")
    private Priority priority;

    @NotNull(message = "Is active is required")
    private Boolean isActive;
}
