package com.cityfix.backend.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Size(max = 1000, message = "Ghi chú duyệt không được vượt quá 1000 ký tự")
    private String reviewNote;

    @NotEmpty(message = "Danh sách sự cố phụ không được để trống")
    private List<@NotNull(message = "Mã sự cố phụ không được null") UUID> subCategoryIds;

    @NotEmpty(message = "Danh sách bước xử lý không được để trống")
    private List<@NotNull(message = "Bước xử lý không được null") @Valid StepAssignmentRequest> steps;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StepAssignmentRequest {

        @NotNull(message = "Mã bước xử lý (stepId) không được để trống")
        private UUID stepId;

        @NotNull(message = "Mã nhân viên phân công (assigneeId) không được để trống")
        private UUID assigneeId;

        private List<@NotNull(message = "ID bước phụ thuộc không được null") UUID> dependsOnStepIds;

        @Future(message = "Hạn chót của bước xử lý không được ở quá khứ")
        private LocalDateTime deadline;

        @Size(max = 1000, message = "Ghi chú bước xử lý không được vượt quá 1000 ký tự")
        private String notes;
    }
}
