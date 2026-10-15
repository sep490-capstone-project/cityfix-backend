package com.cityfix.backend.model.dto.request;

import com.cityfix.backend.model.enums.DepartmentLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateDepartmentRequest(
    @NotBlank(message = "Tên phòng ban không được để trống")
    String name,

    @NotNull(message = "Cấp quản lý không được để trống")
    DepartmentLevel level,

    @Email(message = "Email liên hệ không hợp lệ")
    String contactEmail,

    String phoneNumber,

    UUID managerId
) {
    public CreateDepartmentRequest(String name, DepartmentLevel level, String contactEmail, String phoneNumber) {
        this(name, level, contactEmail, phoneNumber, null);
    }
}
