package com.cityfix.backend.service;

import com.cityfix.backend.enums.AccountStatus;
import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.UserMapper;
import com.cityfix.backend.model.dto.request.LoginRequest;
import com.cityfix.backend.model.dto.request.UserRegisterRequest;
import com.cityfix.backend.model.dto.response.LoginResponse;
import com.cityfix.backend.model.dto.response.UserRegisterResponse;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.UserRole;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private com.cityfix.backend.repository.RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private OtpService otpService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserServiceImpl userService;

    private UserRegisterRequest registerRequest;
    private User mockUser;

    @BeforeEach
    void setUp() {
        registerRequest = UserRegisterRequest.builder()
                .email("nguyenvana@gmail.com")
                .password("password123")
                .fullName("Nguyen Van A")
                .phoneNumber("0912345678")
                .build();

        mockUser = new User();
        mockUser.setUserId(UUID.randomUUID());
        mockUser.setEmail("nguyenvana@gmail.com");
        mockUser.setPasswordHash("encodedPassword123");
        mockUser.setFullName("Nguyen Van A");
        mockUser.setPhoneNumber("0912345678");
        mockUser.setRole(new com.cityfix.backend.model.entity.Role(java.util.UUID.randomUUID(), "RESIDENT"));
        mockUser.setStatus(UserStatus.ACTIVE);
        mockUser.setEmailVerified(true);
    }

    @Test
    @DisplayName("registerCustomer_validRequest_savesUserAndSendsOtp: Luồng đăng ký thành công")
    void registerCustomer_validRequest_savesUserAndSendsOtp() {
        when(userRepository.findByEmail("nguyenvana@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.empty());
        when(userMapper.toUser(any(UserRegisterRequest.class))).thenReturn(mockUser);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword123");
        when(roleRepository.findByRoleName("RESIDENT")).thenReturn(Optional.of(mockUser.getRole()));
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        when(otpService.generateAndStore("nguyenvana@gmail.com")).thenReturn("123456");
        doNothing().when(emailService).sendOtpEmail("nguyenvana@gmail.com", "123456");

        UserRegisterResponse expectedResponse = UserRegisterResponse.builder()
                .id(mockUser.getUserId())
                .email("nguyenvana@gmail.com")
                .fullName("Nguyen Van A")
                .role("RESIDENT")
                .status(AccountStatus.INACTIVE)
                .createdAt(LocalDateTime.now())
                .nextStep("VERIFY_EMAIL")
                .build();


        when(userMapper.toCustomerRegisterResponse(any(User.class))).thenReturn(expectedResponse);

        UserRegisterResponse actualResponse = userService.registerUser(registerRequest);

        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getEmail()).isEqualTo("nguyenvana@gmail.com");
        assertThat(actualResponse.getNextStep()).isEqualTo("VERIFY_EMAIL");

        verify(passwordEncoder, times(1)).encode("password123");
        verify(userRepository, times(1)).save(any(User.class));
        verify(otpService, times(1)).generateAndStore("nguyenvana@gmail.com");
        verify(emailService, times(1)).sendOtpEmail("nguyenvana@gmail.com", "123456");
    }

    @Test
    @DisplayName("registerCustomer_duplicateEmail_throwsConflict: Trùng email ném ra EMAIL_ALREADY_EXISTS")
    void registerCustomer_duplicateEmail_throwsConflict() {
        mockUser.setEmailVerified(true);
        when(userRepository.findByEmail("nguyenvana@gmail.com")).thenReturn(Optional.of(mockUser));

        assertThatThrownBy(() -> userService.registerUser(registerRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).save(any());
        verify(otpService, never()).generateAndStore(any());
    }

    @Test
    @DisplayName("registerCustomer_duplicatePhone_throwsConflict: Trùng SĐT ném ra PHONE_ALREADY_EXISTS")
    void registerCustomer_duplicatePhone_throwsConflict() {
        when(userRepository.findByEmail("nguyenvana@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.of(mockUser));

        assertThatThrownBy(() -> userService.registerUser(registerRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PHONE_ALREADY_EXISTS);

        verify(userRepository, never()).save(any());
        verify(otpService, never()).generateAndStore(any());
    }

    @Test
    @DisplayName("login_validCredentials_returnsTokenResponse: Đăng nhập thành công trả về cặp token")
    void login_validCredentials_returnsTokenResponse() {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("nguyenvana@gmail.com")
                .password("password123")
                .build();

        when(userRepository.findByEmailAndIsDeletedFalse("nguyenvana@gmail.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "encodedPassword123")).thenReturn(true);
        when(tokenService.generateAccessToken(mockUser)).thenReturn("access-token-123");
        when(tokenService.generateRefreshToken(mockUser)).thenReturn("refresh-token-123");

        LoginResponse loginResponse = userService.login(loginRequest);

        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.getAccessToken()).isEqualTo("access-token-123");
        assertThat(loginResponse.getRefreshToken()).isEqualTo("refresh-token-123");

        verify(tokenService, times(1)).generateAccessToken(mockUser);
        verify(tokenService, times(1)).generateRefreshToken(mockUser);
    }

    @Test
    @DisplayName("login_wrongPassword_throwsUnauthorized: Sai mật khẩu ném INVALID_PASSWORD")
    void login_wrongPassword_throwsUnauthorized() {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("nguyenvana@gmail.com")
                .password("wrongpassword")
                .build();

        when(userRepository.findByEmailAndIsDeletedFalse("nguyenvana@gmail.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrongpassword", "encodedPassword123")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);

        verify(tokenService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("logout_callsTokenServiceBlacklist: Đăng xuất gọi đúng phương thức vô hiệu hóa token")
    void logout_callsTokenServiceBlacklist() {
        String token = "jwt-access-token-xyz";

        doNothing().when(tokenService).blacklistToken(token);

        userService.logout(token);

        verify(tokenService, times(1)).blacklistToken(token);
    }

    @Test
    @DisplayName("getMyProfile_authenticatedUser_returnsProfileResponse")
    void getMyProfile_authenticatedUser_returnsProfileResponse() {
        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.getPrincipal()).thenReturn(mockUser);
        when(auth.isAuthenticated()).thenReturn(true);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        com.cityfix.backend.model.dto.response.UserProfileResponse profileResponse =
                com.cityfix.backend.model.dto.response.UserProfileResponse.builder()
                        .email("nguyenvana@gmail.com")
                        .roleName("USER")
                        .fullName("Nguyen Van A")
                        .phoneNumber("0912345678")
                        .build();

        when(userRepository.findByEmailAndIsDeletedFalse(anyString())).thenReturn(Optional.of(mockUser));
        when(userMapper.toUserProfileResponse(mockUser)).thenReturn(profileResponse);

        com.cityfix.backend.model.dto.response.UserProfileResponse result = userService.getMyProfile();

        assertThat(result).isNotNull();
        assertThat(result.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(result.getPhoneNumber()).isEqualTo("0912345678");

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("updateMyProfile_validRequest_updatesAndReturnsProfile")
    void updateMyProfile_validRequest_updatesAndReturnsProfile() {
        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.getPrincipal()).thenReturn(mockUser);
        when(auth.isAuthenticated()).thenReturn(true);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        com.cityfix.backend.model.dto.request.UpdateProfileRequest updateRequest =
                com.cityfix.backend.model.dto.request.UpdateProfileRequest.builder()
                        .fullName("Nguyen Van B")
                        .phoneNumber("0987654321")
                        .address("123 Le Loi")
                        .province("TP.HCM")
                        .ward("Phuong 1")
                        .build();

        com.cityfix.backend.model.dto.response.UserProfileResponse updatedResponse =
                com.cityfix.backend.model.dto.response.UserProfileResponse.builder()
                        .email("nguyenvana@gmail.com")
                        .roleName("USER")
                        .fullName("Nguyen Van B")
                        .phoneNumber("0987654321")
                        .address("123 Le Loi")
                        .province("TP.HCM")
                        .ward("Phuong 1")
                        .build();

        when(userRepository.findByEmailAndIsDeletedFalse(anyString())).thenReturn(Optional.of(mockUser));
        when(userRepository.findByPhoneNumber("0987654321")).thenReturn(Optional.empty());
        when(userMapper.toUserProfileResponse(mockUser)).thenReturn(updatedResponse);

        com.cityfix.backend.model.dto.response.UserProfileResponse result = userService.updateMyProfile(updateRequest);

        assertThat(result).isNotNull();
        assertThat(result.getFullName()).isEqualTo("Nguyen Van B");

        verify(userMapper, times(1)).updateUserFromRequest(updateRequest, mockUser);
        verify(userRepository, times(1)).save(mockUser);

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
