package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePhoneRequest {

    @NotBlank(message = "Firebase ID Token không được để trống")
    private String firebaseIdToken;
}

