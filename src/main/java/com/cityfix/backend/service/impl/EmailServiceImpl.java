package com.cityfix.backend.service.impl;

import com.cityfix.backend.service.EmailService;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final Resend resend;
    private final String fromEmail;

    public EmailServiceImpl(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from-email}") String fromEmail
    ) {
        this.resend    = new Resend(apiKey);
        this.fromEmail = fromEmail;
    }

    @Override
    public void sendOtpEmail(String to, String otp) {
        String subject = "Verify your email";
        String body    = buildEmailBody(otp);

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(to)
                .subject(subject)
                .html(body)
                .build();

        try {
            resend.emails().send(options);
        } catch (ResendException e) {
            log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
        }
    }

    private String buildEmailBody(String otp) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 32px; border: 1px solid #e5e7eb; border-radius: 8px;">
                  <h2 style="color: #1d4ed8; margin-bottom: 8px;">Xác thực tài khoản CityFix của bạn</h2>
                  <p style="color: #374151; font-size: 15px;">
                    Sử dụng mã dùng 1 lần dưới đây để xác minh địa chỉ email của bạn.
                    Mã này sẽ hết hạn trong <strong>5 phút</strong> nữa.
                  </p>
                  <div style="font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #1d4ed8; text-align: center; padding: 24px 0;">
                    %s
                  </div>
                  <p style="color: #6b7280; font-size: 13px;">
                    Nếu bạn không tạo tài khoản, xin vui lòng bỏ qua email này.
                  </p>
                </div>
                """.formatted(otp);
    }

    @Override
    public void sendTicketStatusEmail(String to, String title, String message) {
        String body = """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 32px; border: 1px solid #e5e7eb; border-radius: 8px;">
                  <h2 style="color: #1d4ed8; margin-bottom: 8px;">%s</h2>
                  <p style="color: #374151; font-size: 15px;">
                    %s
                  </p>
                </div>
                """.formatted(title, message);

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(to)
                .subject(title)
                .html(body)
                .build();

        try {
            resend.emails().send(options);
        } catch (ResendException e) {
            log.error("Failed to send status email to {}: {}", to, e.getMessage());
        }
    }
}
