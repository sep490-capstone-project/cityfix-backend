package com.cityfix.backend.model.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record RoutingRuleResponse(
    UUID ruleId,
    UUID departmentId,
    String departmentName,
    UUID categoryId,
    String categoryName,
    String province,
    String ward,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
