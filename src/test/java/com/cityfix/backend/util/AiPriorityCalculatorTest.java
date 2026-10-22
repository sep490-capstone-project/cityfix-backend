package com.cityfix.backend.util;

import com.cityfix.backend.model.dto.request.AiAnalyzeRequest;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiPriorityCalculatorTest {

    @Test
    @DisplayName("convertBaseScore_validPriorities_returnsCorrectStandardScores")
    void convertBaseScore_validPriorities_returnsCorrectStandardScores() {
        assertThat(AiPriorityCalculator.convertBaseScore(Priority.LOW)).isEqualTo(3.0);
        assertThat(AiPriorityCalculator.convertBaseScore(Priority.MEDIUM)).isEqualTo(6.0);
        assertThat(AiPriorityCalculator.convertBaseScore(Priority.HIGH)).isEqualTo(9.0);
        assertThat(AiPriorityCalculator.convertBaseScore(null)).isEqualTo(3.0);
    }

    @Test
    @DisplayName("convertMaxBaseScore_multiSubCategories_returnsHighestScore")
    void convertMaxBaseScore_multiSubCategories_returnsHighestScore() {
        // Đa sự cố: Ổ gà (LOW = 3.0) và Vỡ cống (HIGH = 9.0) -> Base score lấy mức cao nhất 9.0
        assertThat(AiPriorityCalculator.convertMaxBaseScore(List.of(Priority.LOW, Priority.HIGH))).isEqualTo(9.0);
        assertThat(AiPriorityCalculator.convertMaxBaseScore(List.of(Priority.LOW, Priority.MEDIUM))).isEqualTo(6.0);
        assertThat(AiPriorityCalculator.convertMaxBaseScore(List.of(Priority.LOW))).isEqualTo(3.0);
        assertThat(AiPriorityCalculator.convertMaxBaseScore(List.of())).isEqualTo(3.0);
        assertThat(AiPriorityCalculator.convertMaxBaseScore(null)).isEqualTo(3.0);
    }

    @Test
    @DisplayName("calculatePriorityScore_userExamplePothole_returns6Point95AndMediumPriority")
    void calculatePriorityScore_userExamplePothole_returns6Point95AndMediumPriority() {
        // Sự cố "Ổ gà":
        // Database: Mặc định là LOW (B = 3.0)
        // AI: S = 8.0, I = 9.0, U = 9.0, C = 8.0
        double baseScore = AiPriorityCalculator.convertBaseScore(Priority.LOW);
        double S = 8.0;
        double I = 9.0;
        double U = 9.0;
        double C = 8.0;

        double finalScore = AiPriorityCalculator.calculatePriorityScore(baseScore, S, I, U, C);

        // Công thức: 0.30*3 + 0.15*8 + 0.20*9 + 0.25*9 + 0.10*8 = 0.9 + 1.2 + 1.8 + 2.25 + 0.8 = 6.95
        assertThat(finalScore).isEqualTo(6.95);

        // Ánh xạ mờ: 6.95 nằm trong [5.0, 7.5) -> MEDIUM
        Priority priority = AiPriorityCalculator.mapScoreToPriority(finalScore);
        assertThat(priority).isEqualTo(Priority.MEDIUM);

        // SeverityLevel: S = 8.0 -> HIGH
        SeverityLevel severityLevel = AiPriorityCalculator.mapScoreToSeverityLevel(S);
        assertThat(severityLevel).isEqualTo(SeverityLevel.HIGH);
    }

    @Test
    @DisplayName("calculatePriorityScore_extremeDanger_escalatesToHigh")
    void calculatePriorityScore_extremeDanger_escalatesToHigh() {
        // Base Score là LOW (3.0) nhưng rủi ro AI đạt điểm tuyệt đối 10.0
        double baseScore = 3.0;
        double score = AiPriorityCalculator.calculatePriorityScore(baseScore, 10.0, 10.0, 10.0, 10.0);

        // 0.30*3 + 0.70*10 = 0.9 + 7.0 = 7.90
        assertThat(score).isEqualTo(7.90);
        assertThat(AiPriorityCalculator.mapScoreToPriority(score)).isEqualTo(Priority.HIGH);
        assertThat(AiPriorityCalculator.mapScoreToSeverityLevel(10.0)).isEqualTo(SeverityLevel.CRITICAL);
    }

    @Test
    @DisplayName("calculatePriorityScore_lowRisk_staysLow")
    void calculatePriorityScore_lowRisk_staysLow() {
        double baseScore = 3.0;
        double score = AiPriorityCalculator.calculatePriorityScore(baseScore, 2.0, 2.0, 2.0, 2.0);

        // 0.30*3 + 0.70*2 = 0.9 + 1.4 = 2.30
        assertThat(score).isEqualTo(2.30);
        assertThat(AiPriorityCalculator.mapScoreToPriority(score)).isEqualTo(Priority.LOW);
        assertThat(AiPriorityCalculator.mapScoreToSeverityLevel(2.0)).isEqualTo(SeverityLevel.LOW);
    }

    @Test
    @DisplayName("clampScore_outOfBounds_clampsCorrectly")
    void clampScore_outOfBounds_clampsCorrectly() {
        assertThat(AiPriorityCalculator.clampScore(null, 5.0)).isEqualTo(5.0);
        assertThat(AiPriorityCalculator.clampScore(-2.0, 5.0)).isEqualTo(1.0);
        assertThat(AiPriorityCalculator.clampScore(0.5, 5.0)).isEqualTo(1.0);
        assertThat(AiPriorityCalculator.clampScore(12.5, 5.0)).isEqualTo(10.0);
        assertThat(AiPriorityCalculator.clampScore(7.5, 5.0)).isEqualTo(7.5);
    }

    @Test
    @DisplayName("extractAllMediaUrls_singleAndMultiUrl_extractsProperly")
    void extractAllMediaUrls_singleAndMultiUrl_extractsProperly() {
        // Test single URL
        AiAnalyzeRequest req1 = new AiAnalyzeRequest();
        req1.setMediaUrl("https://example.com/single.jpg");
        assertThat(req1.extractAllMediaUrls()).containsExactly("https://example.com/single.jpg");
        assertThat(req1.isValidMediaProvided()).isTrue();

        // Test multi URLs
        AiAnalyzeRequest req2 = new AiAnalyzeRequest();
        req2.setMediaUrls(List.of("https://example.com/1.jpg", "https://example.com/2.jpg", " "));
        assertThat(req2.extractAllMediaUrls()).containsExactly("https://example.com/1.jpg", "https://example.com/2.jpg");
        assertThat(req2.isValidMediaProvided()).isTrue();

        // Test empty request
        AiAnalyzeRequest req3 = new AiAnalyzeRequest();
        assertThat(req3.extractAllMediaUrls()).isEmpty();
        assertThat(req3.isValidMediaProvided()).isFalse();
    }
}
