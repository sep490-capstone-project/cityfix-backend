package com.cityfix.backend.util;

import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.SeverityLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Thuật toán tính toán độ ưu tiên đa biến (Multi-criteria Priority Engine):
 * Kết hợp Base Score từ Database (làm mỏ neo nghiệp vụ) và 4 thông số AI đánh giá bối cảnh thị giác.
 */
public final class AiPriorityCalculator {

    // Phân bổ trọng số cho 5 biến (Tổng = 1.0 hay 100%)
    public static final double WEIGHT_BASE = 0.30;      // wb: 30% Base Score từ DB
    public static final double WEIGHT_URGENCY = 0.25;   // wu: 25% Tính khẩn cấp từ AI
    public static final double WEIGHT_IMPACT = 0.20;    // wi: 20% Phạm vi ảnh hưởng từ AI
    public static final double WEIGHT_SEVERITY = 0.15;  // ws: 15% Mức độ hư hại vật lý từ AI
    public static final double WEIGHT_CONTEXT = 0.10;   // wc: 10% Bối cảnh xung quanh từ AI

    // Quy chuẩn thang điểm 10 cho Base Score từ Database
    public static final double BASE_SCORE_LOW = 3.0;     // LOW -> 3 điểm (Mức độ cơ bản, không gấp)
    public static final double BASE_SCORE_MEDIUM = 6.0;  // MEDIUM -> 6 điểm (Cần xử lý sớm)
    public static final double BASE_SCORE_HIGH = 9.0;    // HIGH -> 9 điểm (Nghiêm trọng, khẩn cấp)

    // Ngưỡng phân loại kết quả (Fuzzy Mapping thresholds)
    public static final double THRESHOLD_MEDIUM = 5.0;  // 0.0 - 4.9 -> LOW, 5.0 - 7.4 -> MEDIUM
    public static final double THRESHOLD_HIGH = 7.5;    // 7.5 - 10.0 -> HIGH

    private AiPriorityCalculator() {
        // Utility class
    }

    /**
     * Bước 1: Quy chuẩn điểm Base Score từ Database
     * LOW -> 3.0
     * MEDIUM -> 6.0
     * HIGH -> 9.0
     */
    public static double convertBaseScore(Priority dbPriority) {
        if (dbPriority == null) {
            return BASE_SCORE_LOW;
        }
        return switch (dbPriority) {
            case HIGH -> BASE_SCORE_HIGH;
            case MEDIUM -> BASE_SCORE_MEDIUM;
            case LOW -> BASE_SCORE_LOW;
        };
    }

    /**
     * Quy đổi điểm Base Score cho danh sách đa sự cố:
     * Lấy Base Score cao nhất trong các sự cố để đảm bảo an toàn kỹ thuật cao nhất
     */
    public static double convertMaxBaseScore(java.util.Collection<Priority> priorities) {
        if (priorities == null || priorities.isEmpty()) {
            return BASE_SCORE_LOW;
        }
        return priorities.stream()
                .mapToDouble(AiPriorityCalculator::convertBaseScore)
                .max()
                .orElse(BASE_SCORE_LOW);
    }

    /**
     * Giới hạn điểm số từ AI trong khoảng [1.0, 10.0]
     */
    public static double clampScore(Double score, double defaultValue) {
        if (score == null) {
            return defaultValue;
        }
        if (score < 1.0) return 1.0;
        if (score > 10.0) return 10.0;
        return score;
    }

    /**
     * Bước 2: Thiết lập công thức tính điểm ưu tiên cuối cùng P
     * P = wb*B + ws*S + wi*I + wu*U + wc*C
     */
    public static double calculatePriorityScore(double baseScore, double severity, double impact, double urgency, double context) {
        double rawScore = (WEIGHT_BASE * baseScore)
                + (WEIGHT_SEVERITY * severity)
                + (WEIGHT_IMPACT * impact)
                + (WEIGHT_URGENCY * urgency)
                + (WEIGHT_CONTEXT * context);
        return BigDecimal.valueOf(rawScore)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Bước 4: Ánh xạ kết quả (Fuzzy Mapping)
     * 0.0 - 4.9 -> LOW
     * 5.0 - 7.4 -> MEDIUM
     * 7.5 - 10.0 -> HIGH
     */
    public static Priority mapScoreToPriority(double score) {
        if (score < THRESHOLD_MEDIUM) {
            return Priority.LOW;
        } else if (score < THRESHOLD_HIGH) {
            return Priority.MEDIUM;
        } else {
            return Priority.HIGH;
        }
    }

    /**
     * Ánh xạ điểm Severity (1-10) sang enum SeverityLevel:
     * 1 - 3 -> LOW
     * 4 - 6 -> MEDIUM
     * 7 - 8 -> HIGH
     * 9 - 10 -> CRITICAL
     */
    public static SeverityLevel mapScoreToSeverityLevel(double severityScore) {
        if (severityScore >= 9.0) {
            return SeverityLevel.CRITICAL;
        } else if (severityScore >= 7.0) {
            return SeverityLevel.HIGH;
        } else if (severityScore >= 4.0) {
            return SeverityLevel.MEDIUM;
        } else {
            return SeverityLevel.LOW;
        }
    }
}
