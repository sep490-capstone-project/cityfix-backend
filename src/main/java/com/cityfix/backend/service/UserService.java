package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.LoginRequest;
import com.cityfix.backend.model.dto.request.ResendOtpRequest;
import com.cityfix.backend.model.dto.request.ResetPasswordRequest;
import com.cityfix.backend.model.dto.request.UserRegisterRequest;
import com.cityfix.backend.model.dto.request.VerifyOtpRequest;
import com.cityfix.backend.model.dto.response.LoginResponse;
import com.cityfix.backend.model.dto.response.UserRegisterResponse;

public interface UserService {
    UserRegisterResponse registerUser(UserRegisterRequest request);
    LoginResponse login(LoginRequest request);
    void logout(String accessToken);
    // [LEGACY EMAIL OTP - COMMENTED OUT FOR FIREBASE PHONE AUTH MIGRATION]
    // void verifyEmail(VerifyOtpRequest request);
    // void resendOtp(ResendOtpRequest request);
    LoginResponse verifyPhoneByFirebaseToken(String firebaseIdToken);
    void resetPassword(ResetPasswordRequest request);
    com.cityfix.backend.model.dto.response.UserProfileResponse getMyProfile();
    com.cityfix.backend.model.dto.response.UserProfileResponse updateMyProfile(com.cityfix.backend.model.dto.request.UpdateProfileRequest request);
}
