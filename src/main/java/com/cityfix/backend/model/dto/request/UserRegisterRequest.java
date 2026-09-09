package com.cityfix.backend.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRegisterRequest {
    private String email;
    private String password;
    private String fullName;
    private String phoneNumber;
    private String province;
    private String ward;
    private String address;
}
