package com.cityfix.backend.model.dto.request;

import com.cityfix.backend.model.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserRequest {
    @NotBlank(message = "Email is required")
    private String email;

    private String password; // Required for create, optional for update

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Province is required")
    private String province;

    @NotBlank(message = "Ward is required")
    private String ward;

    @NotBlank(message = "Address is required")
    private String address;

    @NotNull(message = "Role ID is required")
    private UUID roleId;

    private UUID departmentId; // Optional (legacy / primary department)
    private java.util.List<UUID> departmentIds; // Optional (N-N departments)
    private UserStatus status; // Optional
}
