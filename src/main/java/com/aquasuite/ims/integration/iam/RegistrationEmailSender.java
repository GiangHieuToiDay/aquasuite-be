package com.aquasuite.ims.integration.iam;

import com.aquasuite.ims.exception.iam.RegistrationErrorCode;
import com.aquasuite.ims.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Gửi email chứa mã OTP xác minh đăng ký tài khoản. Gửi đồng bộ để báo được lỗi gửi mail cho người
 * dùng trước khi giữ chỗ OTP.
 */
@Component
@RequiredArgsConstructor
public class RegistrationEmailSender {
    private final JavaMailSender mailSender;

    /** Địa chỉ gửi, lấy từ cấu hình mail; để trống thì dùng mặc định của máy chủ mail. */
    @Value("${spring.mail.username:}")
    private String from;

    /** Gửi mã OTP đăng ký tới email (hiệu lực 5 phút); lỗi mail được quy về MAIL_UNAVAILABLE. */
    public void sendRegistrationOtp(String toEmail, String otpCode) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            if (from != null && !from.isBlank()) message.setFrom(from);
            message.setSubject("[AquaSuite] Registration Verification Code");
            message.setText("Your registration verification code is: " + otpCode
                    + "\nThis code expires 5 minutes after registration.");
            mailSender.send(message);
        } catch (MailException ex) {
            throw new AppException(RegistrationErrorCode.MAIL_UNAVAILABLE);
        }
    }
}
