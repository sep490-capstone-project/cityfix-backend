package com.cityfix.backend.model.dto.request;

import com.cityfix.backend.model.enums.DepartmentLevel;
import jakarta.validation.constraints.Email;

import java.util.UUID;

public record UpdateDepartmentRequest(
    String name,
    DepartmentLevel level,
    @Email(message = "Email liên hệ không hợp lệ")
    String contactEmail,
    String phoneNumber,
    Boolean isActive,
    UUID managerId
) {
    public UpdateDepartmentRequest(String name, DepartmentLevel level, String contactEmail, String phoneNumber, Boolean isActive) {
        this(name, level, contactEmail, phoneNumber, isActive, null);
    }
}
