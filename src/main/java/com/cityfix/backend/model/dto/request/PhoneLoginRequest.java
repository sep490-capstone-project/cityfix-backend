package com.cityfix.backend.model.dto.request;

import com.cityfix.backend.util.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneLoginRequest {

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = ValidationPatterns.PHONE_REGEX, message = ValidationPatterns.PHONE_MESSAGE)
    private String phoneNumber;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(max = 100, message = "Mật khẩu không được vượt quá 100 ký tự")
    private String password;
}

