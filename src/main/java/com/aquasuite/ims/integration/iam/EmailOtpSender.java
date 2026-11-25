package com.aquasuite.ims.integration.iam;

import com.aquasuite.ims.enums.iam.RecoveryChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Gửi mã OTP khôi phục mật khẩu qua email. Chạy bất đồng bộ nên lỗi gửi chỉ được ghi log, không làm
 * hỏng request quên mật khẩu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailOtpSender implements OtpSenderStrategy {

    private final JavaMailSender mailSender;

    /** Chiến lược này phục vụ kênh email. */
    @Override
    public RecoveryChannel getSupportedChannel() {
        return RecoveryChannel.EMAIL;
    }

    /** Gửi email chứa mã OTP 6 chữ số, hiệu lực 10 phút, trên luồng nền. */
    @Async("taskExecutor")
    @Override
    public void sendRecoveryOtp(String toEmail, String otpCode) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("[AquaSuite] Password Recovery Verification Code");
            message.setText("Your 6-digit password reset code is: " + otpCode 
                    + "\nThis code will expire in 10 minutes. If you did not request this, please ignore this email.");
            mailSender.send(message);
            log.info("Dispatched password recovery OTP to email: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password recovery OTP to email {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
