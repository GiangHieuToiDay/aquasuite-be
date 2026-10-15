package com.aquasuite.ims.module.iam.internal.service.otp;

import com.aquasuite.ims.module.iam.internal.entity.enums.RecoveryChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailOtpSender implements OtpSenderStrategy {

    private final JavaMailSender mailSender;

    @Override
    public RecoveryChannel getSupportedChannel() {
        return RecoveryChannel.EMAIL;
    }

    @Async
    @Override
    public void sendRecoveryOtp(String toEmail, String otpCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[AquaSuite] Password Recovery Verification Code");
        message.setText("Your 6-digit password reset code is: " + otpCode 
                + "\nThis code will expire in 10 minutes. If you did not request this, please ignore this email.");
        mailSender.send(message);
        log.info("Dispatched password recovery OTP to email: {}", toEmail);
    }
}
