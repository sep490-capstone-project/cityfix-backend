package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.UserMapper;
import com.cityfix.backend.model.dto.request.LoginRequest;
import com.cityfix.backend.model.dto.request.ResendOtpRequest;
import com.cityfix.backend.model.dto.request.ResetPasswordRequest;
import com.cityfix.backend.model.dto.request.UserRegisterRequest;
import com.cityfix.backend.model.dto.request.VerifyOtpRequest;
import com.cityfix.backend.model.dto.response.LoginResponse;
import com.cityfix.backend.model.dto.response.UserRegisterResponse;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.repository.RoleRepository;
import com.cityfix.backend.service.EmailService;
import com.cityfix.backend.service.OtpService;
import com.cityfix.backend.service.TokenService;
import com.cityfix.backend.service.UserService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cityfix.backend.util.SecurityUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OtpService otpService;
    private final EmailService emailService;

    @Override
    @Transactional
    public UserRegisterResponse registerUser(UserRegisterRequest request) {

        java.util.Optional<User> existingUserOpt = userRepository.findByEmail(request.getEmail());
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if (existingUser.isDeleted()) {
                throw new AppException(ErrorCode.ACCOUNT_REACTIVATION_REQUIRED);
            }
            if (existingUser.isEmailVerified()) {
                throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
            } else {
                existingUser.setFullName(request.getFullName());
                existingUser.setPhoneNumber(request.getPhoneNumber());
                existingUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                userRepository.save(existingUser);

                // [LEGACY EMAIL OTP - COMMENTED OUT FOR FIREBASE PHONE AUTH MIGRATION]
                // String otp = otpService.generateAndStore(request.getEmail());
                // emailService.sendOtpEmail(request.getEmail(), otp);

                UserRegisterResponse response = userMapper.toCustomerRegisterResponse(existingUser);
                // response.setNextStep("VERIFY_EMAIL");
                response.setNextStep("VERIFY_PHONE");
                return response;
            }
        }

        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        User user = userMapper.toUser(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        Role residentRole = roleRepository.findByRoleName("RESIDENT")
                .orElseThrow(() -> new RuntimeException("Default role not found"));
        user.setRole(residentRole);
        user.setStatus(com.cityfix.backend.model.enums.UserStatus.BLOCKED);
        user.setEmailVerified(false);

        userRepository.save(user);

        // [LEGACY EMAIL OTP - COMMENTED OUT FOR FIREBASE PHONE AUTH MIGRATION]
        // String otp = otpService.generateAndStore(request.getEmail());
        // emailService.sendOtpEmail(request.getEmail(), otp);

        UserRegisterResponse response = userMapper.toCustomerRegisterResponse(user);
        // response.setNextStep("VERIFY_EMAIL");
        response.setNextStep("VERIFY_PHONE");
        return response;
    }

    /*
     * [LEGACY EMAIL OTP - COMMENTED OUT FOR FIREBASE PHONE AUTH MIGRATION]
     *
    @Override
    @Transactional
    public void verifyEmail(VerifyOtpRequest request) {
        otpService.verify(request.getEmail(), request.getOtp());

        User user = userRepository.findByEmailAndIsDeletedFalse(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_VERIFIED);
        }

        // Delete OTP from Redis and activate user
        otpService.delete(request.getEmail());
        user.setEmailVerified(true);
        user.setStatus(com.cityfix.backend.model.enums.UserStatus.ACTIVE);
        userRepository.save(user);
    }
    */

    @Override
    @Transactional
    public LoginResponse verifyPhoneByFirebaseToken(String firebaseIdToken) {
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseIdToken);
            String verifiedPhone = (String) decodedToken.getClaims().get("phone_number");

            if (verifiedPhone == null || verifiedPhone.isBlank()) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            // Chuẩn hóa +84 thành đầu 0 (VD: +84948030249 -> 0948030249)
            String localPhone = verifiedPhone.startsWith("+84")
                    ? "0" + verifiedPhone.substring(3)
                    : verifiedPhone;

            User user = userRepository.findByPhoneNumberAndIsDeletedFalse(localPhone)
                    .or(() -> userRepository.findByPhoneNumberAndIsDeletedFalse(verifiedPhone))
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            user.setEmailVerified(true);
            user.setStatus(com.cityfix.backend.model.enums.UserStatus.ACTIVE);
            userRepository.save(user);

            // Sinh JWT token của hệ thống và trả về cho client tương tự luồng verify/login cũ
            String accessToken = tokenService.generateAccessToken(user);
            String refreshToken = tokenService.generateRefreshToken(user);
            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();
        } catch (FirebaseAuthException e) {
            log.error("Firebase verify token failed: {}", e.getMessage());
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        try {
            // 1. Xác thực và giải mã Firebase ID Token
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(request.getFirebaseIdToken());
            String verifiedPhone = (String) decodedToken.getClaims().get("phone_number");

            if (verifiedPhone == null || verifiedPhone.isBlank()) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            // 2. Chuẩn hóa số điện thoại về định dạng local (ví dụ: +84948030249 -> 0948030249)
            String localPhone = verifiedPhone.startsWith("+84")
                    ? "0" + verifiedPhone.substring(3)
                    : verifiedPhone;

            // 3. Tìm kiếm người dùng trong cơ sở dữ liệu
            User user = userRepository.findByPhoneNumberAndIsDeletedFalse(localPhone)
                    .or(() -> userRepository.findByPhoneNumberAndIsDeletedFalse(verifiedPhone))
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            // 4. Mã hóa mật khẩu mới và lưu
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

        } catch (FirebaseAuthException e) {
            log.error("Firebase token verification failed during password reset: {}", e.getMessage());
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    /*
     * [LEGACY EMAIL OTP - COMMENTED OUT FOR FIREBASE PHONE AUTH MIGRATION]
     *
    @Override
    public void resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByEmailAndIsDeletedFalse(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_VERIFIED);
        }
        if (otpService.isOnCooldown(request.getEmail())) {
            throw new AppException(ErrorCode.OTP_COOLDOWN);
        }

        String otp = otpService.generateAndStore(request.getEmail());
        emailService.sendOtpEmail(request.getEmail(), otp);
    }
    */

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndIsDeletedFalse(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_PASSWORD);
        }
        if (user.getStatus() == com.cityfix.backend.model.enums.UserStatus.BLOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_BANNED);
        }

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user);
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public void logout(String accessToken) {
        tokenService.blacklistToken(accessToken);
    }

    @Override
    @Transactional(readOnly = true)
    public com.cityfix.backend.model.dto.response.UserProfileResponse getMyProfile() {
        String email = getCurrentUserEmail();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        return userMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional
    public com.cityfix.backend.model.dto.response.UserProfileResponse updateMyProfile(com.cityfix.backend.model.dto.request.UpdateProfileRequest request) {
        String email = getCurrentUserEmail();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank() 
                && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
            userRepository.findByPhoneNumber(request.getPhoneNumber()).ifPresent(existingUser -> {
                if (!existingUser.getUserId().equals(user.getUserId())) {
                    throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
                }
            });
        }

        userMapper.updateUserFromRequest(request, user);
        userRepository.save(user);
        return userMapper.toUserProfileResponse(user);
    }

    private String getCurrentUserEmail() {
        return SecurityUtils.getCurrentUserEmail();
    }
}
