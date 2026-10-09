package com.cityfix.backend.model.dto.response;

import com.cityfix.backend.model.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private UUID userId;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String province;
    private String ward;
    private String address;
    private String roleName;
    private UUID roleId;
    private UUID departmentId;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
