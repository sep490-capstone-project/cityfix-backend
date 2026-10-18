package com.cityfix.backend.model.dto.response;

import com.cityfix.backend.model.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class UserRegisterResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String role;
    private UserStatus status;
    private LocalDateTime createdAt;
    private String nextStep;
}
