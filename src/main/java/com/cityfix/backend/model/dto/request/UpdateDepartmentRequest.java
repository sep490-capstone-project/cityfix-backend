package com.cityfix.backend.model.dto.request;

import com.cityfix.backend.util.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateDepartmentRequest(
    @NotBlank(message = "Tên phòng ban không được để trống")
    @Size(max = 255, message = "Tên phòng ban không được vượt quá 255 ký tự")
    String name,

    @NotBlank(message = "Email liên hệ không được để trống")
    @Email(message = "Email liên hệ không hợp lệ")
    @Size(max = 150, message = "Email liên hệ không được vượt quá 150 ký tự")
    String contactEmail,

    @NotBlank(message = "Số điện thoại liên hệ không được để trống")
    @Pattern(regexp = ValidationPatterns.PHONE_REGEX, message = ValidationPatterns.PHONE_MESSAGE)
    String phoneNumber,

    Boolean isActive,
    UUID managerId
) {
    public UpdateDepartmentRequest(String name, String contactEmail, String phoneNumber, Boolean isActive) {
        this(name, contactEmail, phoneNumber, isActive, null);
    }
}
