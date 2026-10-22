package com.cityfix.backend.model.dto.response;

import com.cityfix.backend.model.enums.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalyzeResponse {
    // Đa sự cố: danh sách mã và tên các danh mục sự cố chi tiết
    private List<UUID> subCategoryIds;
    private List<String> subCategoryNames;

    private Priority priority;
    private BigDecimal confidenceScore;
    private String description;
    private Boolean isValidIssue;

    // 4 thông số AI đánh giá thị giác (thang điểm 1 - 10)
    private Double urgency;
    private Double impact;
    private Double severityScore;
    private Double context;

    // Các thông số tính toán độ ưu tiên đa biến (Priority Engine)
    private Double baseScore;
    private Double finalPriorityScore;
    private String priorityReasoning;
}
