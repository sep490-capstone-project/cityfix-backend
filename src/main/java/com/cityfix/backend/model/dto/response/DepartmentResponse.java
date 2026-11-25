package com.cityfix.backend.model.dto.response;

import java.util.UUID;

public record DepartmentResponse(
    UUID departmentId,
    String name,
    String contactEmail,
    String phoneNumber,
    int officerCount,
    Boolean isActive,
    UUID managerId,
    String managerName
) {
    public DepartmentResponse(UUID departmentId, String name, String contactEmail, String phoneNumber, int officerCount, Boolean isActive) {
        this(departmentId, name, contactEmail, phoneNumber, officerCount, isActive, null, null);
    }
}
