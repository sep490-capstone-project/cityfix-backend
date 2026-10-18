package com.cityfix.backend.controller;

import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.request.LoginRequest;
import com.cityfix.backend.model.dto.request.ResendOtpRequest;
import com.cityfix.backend.model.dto.request.UserRegisterRequest;
import com.cityfix.backend.model.dto.request.VerifyOtpRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.LoginResponse;
import com.cityfix.backend.model.dto.response.UserRegisterResponse;
import com.cityfix.backend.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("register_validRequest_returns201WithData: Đăng ký thành công trả về HTTP 201 cùng response data")
    void register_validRequest_returns201WithData() {
        UserRegisterRequest request = UserRegisterRequest.builder()
                .email("nguyenvana@gmail.com")
                .password("password123")
                .fullName("Nguyen Van A")
                .phoneNumber("0912345678")
                .build();

        UserRegisterResponse expectedData = UserRegisterResponse.builder()
                .id(UUID.randomUUID())
                .email("nguyenvana@gmail.com")
                .fullName("Nguyen Van A")
                .role("RESIDENT")
                .status(UserStatus.BLOCKED)
                .createdAt(LocalDateTime.now())
                .nextStep("VERIFY_EMAIL")
                .build();


        when(userService.registerUser(any(UserRegisterRequest.class))).thenReturn(expectedData);

        ResponseEntity<BaseResponse<UserRegisterResponse>> response = authController.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(expectedData);

        verify(userService, times(1)).registerUser(request);
    }

    @Test
    @DisplayName("login_validCredentials_returns200WithTokens: Đăng nhập thành công chỉ trả về accessToken và refreshToken trong data")
    void login_validCredentials_returns200WithTokens() {
        LoginRequest request = LoginRequest.builder()
                .email("nguyenvana@gmail.com")
                .password("password123")
                .build();

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .build();

        when(userService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        ResponseEntity<BaseResponse<?>> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Đăng nhập thành công");
        
        LoginResponse data = (LoginResponse) response.getBody().getData();
        assertThat(data).isNotNull();
        assertThat(data.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(data.getRefreshToken()).isEqualTo("mock-refresh-token");

        verify(userService, times(1)).login(request);
    }

    @Test
    @DisplayName("login_badCredentials_throwsAppException: Sai mật khẩu quăng ra AppException INVALID_PASSWORD")
    void login_badCredentials_throwsAppException() {
        LoginRequest request = LoginRequest.builder()
                .email("nguyenvana@gmail.com")
                .password("wrongpassword")
                .build();

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new AppException(ErrorCode.INVALID_PASSWORD));

        assertThatThrownBy(() -> authController.login(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Sai mật khẩu");

        verify(userService, times(1)).login(request);
    }

    @Test
    @DisplayName("logout_validToken_returns200Success: Đăng xuất và blacklist token thành công")
    void logout_validToken_returns200Success() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer valid-bearer-token");

        doNothing().when(userService).logout("valid-bearer-token");

        ResponseEntity<BaseResponse<Void>> response = authController.logout(servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Đăng xuất thành công");

        verify(userService, times(1)).logout("valid-bearer-token");
    }

    @Test
    @DisplayName("verifyOtp_validCode_returns200Success: Nhập đúng OTP kích hoạt tài khoản thành công")
    void verifyOtp_validCode_returns200Success() {
        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .email("nguyenvana@gmail.com")
                .otp("123456")
                .build();

        doNothing().when(userService).verifyEmail(any(VerifyOtpRequest.class));

        ResponseEntity<BaseResponse<Void>> response = authController.verifyEmail(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Email đã được xác thực");

        verify(userService, times(1)).verifyEmail(request);
    }

    @Test
    @DisplayName("resendOtp_validEmail_returns200Success: Yêu cầu gửi lại OTP thành công")
    void resendOtp_validEmail_returns200Success() {
        ResendOtpRequest request = ResendOtpRequest.builder()
                .email("nguyenvana@gmail.com")
                .build();

        doNothing().when(userService).resendOtp(any(ResendOtpRequest.class));

        ResponseEntity<BaseResponse<Void>> response = authController.resendOtp(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Mã OPT mới đã được gửi tới email của bạn");

        verify(userService, times(1)).resendOtp(request);
    }
}
