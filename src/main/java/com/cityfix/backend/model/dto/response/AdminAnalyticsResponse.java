package com.cityfix.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAnalyticsResponse {

    private AnalyticsOverview overview;
    private List<TimeSeriesItem> timeSeries;
    private List<GeographicAnalyticsItem> geographicBreakdown;
    private List<DepartmentAnalyticsItem> departmentBreakdown;
    private List<CategoryAnalyticsItem> categoryBreakdown;
    private Map<String, Long> statusBreakdown;
    private Map<String, Long> priorityBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyticsOverview {
        private long totalTickets;
        private long resolvedOrCompletedTickets;
        private long inProgressTickets;
        private long overdueTickets;
        private long rejectedOrCancelledTickets;
        private double resolutionRate;
        private double onTimeResolutionRate;
        private double averageResolutionHours;
        private double averageRating;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesItem {
        private String period;
        private long totalCreated;
        private long resolvedOrCompleted;
        private long inProgress;
        private long rejectedOrCancelled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicAnalyticsItem {
        private String province;
        private String ward;
        private long totalTickets;
        private long resolvedOrCompletedTickets;
        private long inProgressTickets;
        private long overdueTickets;
        private double resolutionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentAnalyticsItem {
        private UUID departmentId;
        private String departmentName;
        private long totalTickets;
        private long resolvedOrCompletedTickets;
        private long inProgressTickets;
        private long overdueTickets;
        private double resolutionRate;
        private double onTimeRate;
        private double averageResolutionHours;
        private double averageRating;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryAnalyticsItem {
        private UUID categoryId;
        private String categoryName;
        private long totalTickets;
        private long resolvedOrCompletedTickets;
        private double resolutionRate;
    }
}
