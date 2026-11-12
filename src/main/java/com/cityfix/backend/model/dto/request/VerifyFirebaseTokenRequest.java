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
public class VerifyFirebaseTokenRequest {

    @NotBlank(message = "Firebase ID Token must not be blank")
    private String firebaseIdToken;
}

