package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.response.AdminAnalyticsResponse;
import com.cityfix.backend.model.dto.response.AdminDashboardSummaryResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AdminDashboardService {

    AdminDashboardSummaryResponse getDashboardSummary();

    AdminAnalyticsResponse getAnalytics(
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String groupBy,
            String province,
            String ward,
            UUID departmentId,
            UUID categoryId
    );

    byte[] exportAnalyticsCsv(
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String groupBy,
            String province,
            String ward,
            UUID departmentId,
            UUID categoryId
    );
}
