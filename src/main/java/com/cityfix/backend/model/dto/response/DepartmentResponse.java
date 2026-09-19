package com.cityfix.backend.model.dto.response;

import com.cityfix.backend.model.enums.DepartmentLevel;

import java.util.UUID;

public record DepartmentResponse(
    UUID departmentId,
    String name,
    DepartmentLevel level,
    String contactEmail,
    String phoneNumber,
    int officerCount,
    Boolean isActive
) {}
