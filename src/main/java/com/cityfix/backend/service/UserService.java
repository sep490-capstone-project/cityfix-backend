package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.LoginRequest;
import com.cityfix.backend.model.dto.request.ResendOtpRequest;
import com.cityfix.backend.model.dto.request.UserRegisterRequest;
import com.cityfix.backend.model.dto.request.VerifyOtpRequest;
import com.cityfix.backend.model.dto.response.LoginResponse;
import com.cityfix.backend.model.dto.response.UserRegisterResponse;

public interface UserService {
    UserRegisterResponse registerUser(UserRegisterRequest request);
    LoginResponse login(LoginRequest request);
    void logout(String accessToken);
    void verifyEmail(VerifyOtpRequest request);
    void resendOtp(ResendOtpRequest request);
}
