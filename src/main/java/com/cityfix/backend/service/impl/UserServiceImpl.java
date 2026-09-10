package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.UserMapper;
import com.cityfix.backend.model.dto.request.LoginRequest;
import com.cityfix.backend.model.dto.request.ResendOtpRequest;
import com.cityfix.backend.model.dto.request.UserRegisterRequest;
import com.cityfix.backend.model.dto.request.VerifyOtpRequest;
import com.cityfix.backend.model.dto.response.LoginResponse;
import com.cityfix.backend.model.dto.response.UserRegisterResponse;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.EmailService;
import com.cityfix.backend.service.OtpService;
import com.cityfix.backend.service.TokenService;
import com.cityfix.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
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

                String otp = otpService.generateAndStore(request.getEmail());
                emailService.sendOtpEmail(request.getEmail(), otp);

                UserRegisterResponse response = userMapper.toCustomerRegisterResponse(existingUser);
                response.setNextStep("VERIFY_EMAIL");
                return response;
            }
        }

        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        User user = userMapper.toUser(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(com.cityfix.backend.model.enums.UserRole.RESIDENT);
        user.setStatus(com.cityfix.backend.model.enums.UserStatus.BLOCKED);
        user.setEmailVerified(false);

        userRepository.save(user);

        // Generate OTP, cache in Redis, send via Resend
        String otp = otpService.generateAndStore(request.getEmail());
        emailService.sendOtpEmail(request.getEmail(), otp);

        UserRegisterResponse response = userMapper.toCustomerRegisterResponse(user);
        response.setNextStep("VERIFY_EMAIL");
        return response;
    }

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
}
