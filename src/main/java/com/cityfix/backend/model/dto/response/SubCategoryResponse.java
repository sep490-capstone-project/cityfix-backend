package com.cityfix.backend.model.dto.response;

import com.cityfix.backend.model.enums.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubCategoryResponse {
    private UUID subCategoryId;
    private UUID categoryId;
    private String name;
    private String description;
    private Integer sla;
    private Priority priority;
    private boolean isActive;
}
