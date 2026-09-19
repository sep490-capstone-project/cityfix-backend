package com.cityfix.backend.model.dto.request;

import com.cityfix.backend.model.enums.DepartmentLevel;
import jakarta.validation.constraints.Email;

public record UpdateDepartmentRequest(
    String name,
    DepartmentLevel level,
    @Email(message = "Email liên hệ không hợp lệ")
    String contactEmail,
    String phoneNumber,
    Boolean isActive
) {}
