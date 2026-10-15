package com.cityfix.backend.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTicketRequest {

    private Boolean isLifeThreatening;

    private String reviewNote;

    @NotEmpty(message = "Danh sách sự cố phụ không được để trống")
    private List<UUID> subCategoryIds;

    @NotEmpty(message = "Danh sách bước xử lý không được để trống")
    @Valid
    private List<StepAssignmentRequest> steps;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StepAssignmentRequest {

        @NotNull(message = "Mã bước xử lý (stepId) không được để trống")
        private UUID stepId;

        @NotNull(message = "Mã nhân viên phân công (assigneeId) không được để trống")
        private UUID assigneeId;

        @NotNull(message = "Thứ tự thực hiện (orderIndex) không được để trống")
        private Integer orderIndex;

        private LocalDateTime deadline;

        private String notes;
    }
}
