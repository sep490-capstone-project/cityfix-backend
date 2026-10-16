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
public class DepartmentUserResponse {
    private UUID userId;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String roleName;
    private UserStatus status;
    private LocalDateTime joinedAt;
}
