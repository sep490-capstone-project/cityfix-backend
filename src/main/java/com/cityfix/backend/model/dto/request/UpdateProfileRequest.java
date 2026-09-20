package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @NotBlank(message = "Tên không được để trống")
    private String fullName;

    @NotBlank(message = "Phone number cannot be blank")
    @Pattern(regexp = "^\\d{10,11}$", message = "Phone number is invalid")
    private String phoneNumber;

    private String address;
    private String province;
    private String ward;
}
