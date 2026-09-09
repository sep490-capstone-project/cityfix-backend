package com.cityfix.backend.service;

public interface OtpService {
    String generateAndStore(String email);
    void verify(String email, String otp);
    void delete(String email);
    boolean isOnCooldown(String email);
}
