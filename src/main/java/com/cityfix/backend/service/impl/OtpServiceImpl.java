package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final String OTP_PREFIX      = "otp:register:";
    private static final String COOLDOWN_PREFIX = "otp:register:cooldown:";
    private static final Duration OTP_TTL       = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL  = Duration.ofSeconds(60);

    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateAndStore(String email) {
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(OTP_PREFIX + email, otp, OTP_TTL);
        stringRedisTemplate.opsForValue().set(COOLDOWN_PREFIX + email, "1", COOLDOWN_TTL);
        return otp;
    }

    @Override
    public void verify(String email, String otp) {
        String stored = stringRedisTemplate.opsForValue().get(OTP_PREFIX + email);
        if (stored == null) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }
        if (!stored.equals(otp)) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }
    }

    @Override
    public void delete(String email) {
        stringRedisTemplate.delete(OTP_PREFIX + email);
    }

    @Override
    public boolean isOnCooldown(String email) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(COOLDOWN_PREFIX + email));
    }
}
