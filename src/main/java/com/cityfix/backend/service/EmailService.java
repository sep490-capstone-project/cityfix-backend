package com.cityfix.backend.service;

public interface EmailService {
    void sendOtpEmail(String to, String otp);
    void sendTicketStatusEmail(String to, String title, String message);
}
