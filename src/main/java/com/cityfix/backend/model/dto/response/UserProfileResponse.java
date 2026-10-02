package com.cityfix.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.cityfix.backend.model.enums.UserStatus;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID userId;
    private String email;
    private String roleName;
    private UUID departmentId;
    private UserStatus status;
    
    private String fullName;
    private String phoneNumber;
    private String address;
    private String province;
    private String ward;
}
