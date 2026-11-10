package com.cityfix.backend.model.dto.response;

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
public class AdminDashboardSummaryResponse {

    private TicketKpiSummary ticketSummary;
    private AccountKpiSummary accountSummary;
    private ConfigKpiSummary configSummary;
    private List<DepartmentKpiItem> departmentSummaries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketKpiSummary {
        private long totalTickets;
        private long initiatedTickets;
        private long assignedTickets;
        private long inProgressTickets;
        private long resolvedTickets;
        private long completedTickets;
        private long rejectedTickets;
        private long cancelledTickets;
        private long unassignedTickets;
        private long needsManualReviewTickets;
        private long lifeThreateningTickets;
        private long overdueTickets;
        private double overallResolutionRate;
        private double averageRating;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountKpiSummary {
        private long totalUsers;
        private long activeUsers;
        private long blockedUsers;
        private long residentCount;
        private long staffCount;
        private long managerCount;
        private long adminCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigKpiSummary {
        private long totalDepartments;
        private long totalCategories;
        private long activeCategories;
        private long totalSubCategories;
        private long activeSubCategories;
        private long totalSteps;
        private long totalRoutingRules;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentKpiItem {
        private UUID departmentId;
        private String departmentName;
        private long totalAssignedTickets;
        private long inProgressTickets;
        private long resolvedOrCompletedTickets;
        private long overdueTickets;
        private double resolutionRate;
    }
}
