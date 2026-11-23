package com.cityfix.backend.model.dto.request;

import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.util.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class AdminUserRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Định dạng email không hợp lệ")
    @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
    private String email;

    @Pattern(regexp = ValidationPatterns.OPTIONAL_PASSWORD_REGEX, message = ValidationPatterns.PASSWORD_MESSAGE)
    private String password; // Required for create, optional for update

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = ValidationPatterns.PHONE_REGEX, message = ValidationPatterns.PHONE_MESSAGE)
    private String phoneNumber;

    @NotBlank(message = "Province is required")
    @Size(max = 100, message = "Tỉnh/Thành phố không được vượt quá 100 ký tự")
    private String province;

    @NotBlank(message = "Ward is required")
    @Size(max = 100, message = "Phường/Xã không được vượt quá 100 ký tự")
    private String ward;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @NotNull(message = "Role ID is required")
    private UUID roleId;

    private UUID departmentId; // Optional (legacy / primary department)
    private List<@NotNull(message = "Mã phòng ban không được null") UUID> departmentIds; // Optional (N-N departments)
    private UserStatus status; // Optional
}
