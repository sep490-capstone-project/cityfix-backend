package com.cityfix.backend.service;

public interface EmailService {
    void sendOtpEmail(String to, String otp);
}
