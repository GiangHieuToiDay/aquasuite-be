package com.aquasuite.ims.module.iam.internal.auth.service;

import com.aquasuite.ims.module.iam.api.event.PasswordChangedEvent;
import com.aquasuite.ims.module.iam.internal.auth.PasswordRecoveryErrorCode;
import com.aquasuite.ims.module.iam.internal.auth.dto.ChangePasswordRequest;
import com.aquasuite.ims.module.iam.internal.auth.dto.ForgotPasswordRequest;
import com.aquasuite.ims.module.iam.internal.auth.dto.PasswordActionResponse;
import com.aquasuite.ims.module.iam.internal.auth.dto.RecoveryChannel;
import com.aquasuite.ims.module.iam.internal.auth.dto.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
    private static final Pattern PASSWORD_POLICY = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final InMemoryPasswordRecoveryStore recoveryStore;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PasswordActionResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(PasswordRecoveryErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) throw new AppException(PasswordRecoveryErrorCode.INCORRECT_CURRENT_PASSWORD);
        validateNewPassword(request.newPassword(), request.confirmNewPassword(), user);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        eventPublisher.publishEvent(new PasswordChangedEvent(user.getId(), "SELF_SERVICE_CHANGE"));
        return new PasswordActionResponse("Password changed successfully. Please log in again if required.");
    }

    public PasswordActionResponse requestRecovery(ForgotPasswordRequest request) {
        if (request.channel() != RecoveryChannel.EMAIL) throw new AppException(PasswordRecoveryErrorCode.SMS_CHANNEL_NOT_SUPPORTED_YET);
        String email = normalize(request.identifier());
        String key = "email:" + email;
        Instant now = Instant.now();
        if (!recoveryStore.canSend(key, now)) throw new AppException(PasswordRecoveryErrorCode.EMAIL_RATE_LIMIT_EXCEEDED);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new AppException(PasswordRecoveryErrorCode.USER_NOT_FOUND));
        String otp = "%06d".formatted(new SecureRandom().nextInt(1_000_000));
        recoveryStore.put(key, user.getId(), passwordEncoder.encode(otp), now);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email); message.setSubject("[AquaSuite] Password Recovery Verification Code");
            message.setText("Your 6-digit password reset code is: " + otp + "\nThis code expires in 10 minutes.");
            mailSender.send(message);
        } catch (Exception ex) { throw new AppException(CommonErrorCode.INTERNAL_SERVER_ERROR); }
        return new PasswordActionResponse("A password reset code has been sent to your email.");
    }

    @Transactional
    public PasswordActionResponse resetPassword(ResetPasswordWithOtpRequest request) {
        String email = normalize(request.identifier());
        InMemoryPasswordRecoveryStore.Challenge challenge = recoveryStore.consumeIfValid("email:" + email, request.otpCode().trim(), passwordEncoder::matches, Instant.now());
        if (challenge == null) throw new AppException(PasswordRecoveryErrorCode.INVALID_OR_EXPIRED_OTP);
        User user = userRepository.findById(challenge.userId()).orElseThrow(() -> new AppException(PasswordRecoveryErrorCode.USER_NOT_FOUND));
        validateNewPassword(request.newPassword(), request.confirmNewPassword(), user);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        eventPublisher.publishEvent(new PasswordChangedEvent(user.getId(), "OTP_RESET"));
        return new PasswordActionResponse("Your password has been reset successfully. You may now sign in.");
    }

    private void validateNewPassword(String password, String confirmation, User user) {
        if (password == null || !password.equals(confirmation) || !PASSWORD_POLICY.matcher(password).matches()) throw new AppException(PasswordRecoveryErrorCode.WEAK_OR_MISMATCHED_PASSWORD);
        if (passwordEncoder.matches(password, user.getPasswordHash())) throw new AppException(PasswordRecoveryErrorCode.SAME_AS_CURRENT_PASSWORD);
    }
    private String normalize(String identifier) { return identifier.trim().toLowerCase(java.util.Locale.ROOT); }
}
