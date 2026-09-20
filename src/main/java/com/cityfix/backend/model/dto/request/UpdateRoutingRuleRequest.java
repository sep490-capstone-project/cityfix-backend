package com.cityfix.backend.model.dto.request;

import java.util.UUID;

public record UpdateRoutingRuleRequest(
    UUID departmentId,
    UUID categoryId,
    String province,
    String ward
) {}
