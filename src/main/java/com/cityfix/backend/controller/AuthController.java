package com.cityfix.backend.controller;

import com.cityfix.backend.mapper.UserMapper;
import com.cityfix.backend.model.dto.request.LoginRequest;
import com.cityfix.backend.model.dto.request.ResendOtpRequest;
import com.cityfix.backend.model.dto.request.UserRegisterRequest;
import com.cityfix.backend.model.dto.request.VerifyOtpRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.LoginResponse;
import com.cityfix.backend.model.dto.response.UserRegisterResponse;
import com.cityfix.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<BaseResponse<UserRegisterResponse>> register(
            @Valid @RequestBody UserRegisterRequest request) {

        UserRegisterResponse data = userService.registerUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.success("Mã OTP đã được gửi. Vui lòng kiểm tra email của bạn.", data));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<BaseResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyOtpRequest request) {

        userService.verifyEmail(request);

        return ResponseEntity
                .ok(BaseResponse.success("Email đã được xác thực", null));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<BaseResponse<Void>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request) {

        userService.resendOtp(request);

        return ResponseEntity
                .ok(BaseResponse.success("Mã OPT mới đã được gửi tới email của bạn", null));
    }

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<?>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse data = userService.login(request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(BaseResponse.success("Đăng nhập thành công", data));
    }

    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            userService.logout(authHeader.substring(7));
        }
        return ResponseEntity
                .ok(BaseResponse.success("Đăng xuất thành công", null));
    }
}
