package com.cityfix.backend.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTicketRequest {
    private UUID categoryId;

    private UUID targetDepartmentId;

    @Valid
    private List<SubCategoryRouteRequest> routes;

    private List<UUID> departmentIds;

    private Boolean isLifeThreatening;

    private String reviewNote;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubCategoryRouteRequest {
        @NotNull(message = "Subcategory ID is required")
        private UUID subCategoryId;

        @NotNull(message = "Department ID is required")
        private UUID departmentId;
    }
}
