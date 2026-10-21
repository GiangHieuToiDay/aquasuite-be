package com.aquasuite.ims.module.iam.internal.service.impl;

import com.aquasuite.ims.module.iam.api.event.PasswordChangedEvent;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.enums.RecoveryChannel;
import com.aquasuite.ims.module.iam.internal.entity.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.exception.IamPasswordErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.ChangePasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.ForgotPasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.PasswordActionResponse;
import com.aquasuite.ims.module.iam.internal.payload.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.security.SecurityUtils;
import com.aquasuite.ims.module.iam.internal.service.PasswordService;
import com.aquasuite.ims.module.iam.internal.service.otp.OtpSenderStrategy;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PasswordServiceImpl implements PasswordService {

    private static final Pattern PASSWORD_POLICY_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final List<OtpSenderStrategy> otpStrategies;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PasswordActionResponse changePassword(ChangePasswordRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new AppException(IamPasswordErrorCode.INCORRECT_CURRENT_PASSWORD);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(IamPasswordErrorCode.USER_NOT_FOUND));

        // Step 5: Check current password (MSG21)
        if (user.getPassword() == null || !passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new AppException(IamPasswordErrorCode.INCORRECT_CURRENT_PASSWORD);
        }

        // Step 6: Validate complexity & match confirmation (MSG22)
        validatePasswordPolicyAndConfirmation(req.newPassword(), req.confirmNewPassword());

        // Step 6: Check new password is not same as current password (IAM_PWD_003)
        validateNotSameAsCurrentPassword(user, req.newPassword());

        // Step 7: Persist new password
        String newHash = passwordEncoder.encode(req.newPassword());
        user.setPassword(newHash);
        user.setPasswordLastChangedAt(Instant.now());
        userRepository.save(user);

        // Publish domain event
        eventPublisher.publishEvent(new PasswordChangedEvent(user.getId(), "CHANGE_PASSWORD"));

        // MSG18
        return new PasswordActionResponse("Password changed successfully. Please log in again if required.");
    }

    @Override
    public PasswordActionResponse requestPasswordRecovery(ForgotPasswordRequest req) {
        String identifier = req.identifier().trim();
        User user;

        if (req.channel() == RecoveryChannel.EMAIL) {
            user = userRepository.findByEmailIgnoreCase(identifier)
                    .orElseThrow(() -> new AppException(IamPasswordErrorCode.USER_NOT_FOUND));
        } else if (req.channel() == RecoveryChannel.SMS) {
            user = userRepository.findByPhone(identifier)
                    .orElseThrow(() -> new AppException(IamPasswordErrorCode.USER_NOT_FOUND));
        } else {
            throw new AppException(IamPasswordErrorCode.USER_NOT_FOUND);
        }

        // Anti-spam rate limiting: max 5 requests per day
        LocalDate today = LocalDate.now();
        if (today.equals(user.getEmailSentDate()) && user.getEmailSentCount() != null && user.getEmailSentCount() >= 5) {
            throw new AppException(IamPasswordErrorCode.EMAIL_RATE_LIMIT_EXCEEDED);
        }

        if (!today.equals(user.getEmailSentDate())) {
            user.setEmailSentDate(today);
            user.setEmailSentCount(1);
        } else {
            user.setEmailSentCount(user.getEmailSentCount() != null ? user.getEmailSentCount() + 1 : 1);
        }

        // Generate 6-digit OTP
        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        user.setVerificationCode(passwordEncoder.encode(otp));
        user.setVerificationExpiredAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        userRepository.save(user);

        // Find strategy and send OTP
        OtpSenderStrategy strategy = otpStrategies.stream()
                .filter(s -> s.getSupportedChannel() == req.channel())
                .findFirst()
                .orElseThrow(() -> new AppException(IamPasswordErrorCode.SMS_CHANNEL_NOT_SUPPORTED_YET));

        strategy.sendRecoveryOtp(identifier, otp);

        // MSG19
        return new PasswordActionResponse("A password reset code has been sent to your email.");
    }

    @Override
    public PasswordActionResponse resetPasswordWithOtp(ResetPasswordWithOtpRequest req) {
        String identifier = req.identifier().trim();

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByPhone(identifier);
        }
        User user = userOpt.orElseThrow(() -> new AppException(IamPasswordErrorCode.USER_NOT_FOUND));

        // Validate OTP existence and expiration (10 min)
        if (user.getVerificationCode() == null || user.getVerificationExpiredAt() == null
                || Instant.now().isAfter(user.getVerificationExpiredAt())) {
            throw new AppException(IamPasswordErrorCode.INVALID_OR_EXPIRED_OTP);
        }

        // Validate OTP code match
        if (!passwordEncoder.matches(req.otpCode().trim(), user.getVerificationCode())) {
            throw new AppException(IamPasswordErrorCode.INVALID_OR_EXPIRED_OTP);
        }

        // Validate new password policy and confirmation (MSG22)
        validatePasswordPolicyAndConfirmation(req.newPassword(), req.confirmNewPassword());

        // Validate not same as current password (IAM_PWD_003)
        validateNotSameAsCurrentPassword(user, req.newPassword());

        // Update password, clear OTP, unlock account if inactive
        String newHash = passwordEncoder.encode(req.newPassword());
        user.setPassword(newHash);
        user.setPasswordLastChangedAt(Instant.now());
        user.setVerificationCode(null);
        user.setVerificationExpiredAt(null);

        if (user.getStatus() == UserStatus.INACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        // Publish domain event
        eventPublisher.publishEvent(new PasswordChangedEvent(user.getId(), "FORGOT_PASSWORD_RESET"));

        // MSG20
        return new PasswordActionResponse("Your password has been reset successfully. You may now sign in.");
    }

    private void validatePasswordPolicyAndConfirmation(String newPassword, String confirmNewPassword) {
        if (newPassword == null || !newPassword.equals(confirmNewPassword)
                || !PASSWORD_POLICY_PATTERN.matcher(newPassword).matches()) {
            throw new AppException(IamPasswordErrorCode.WEAK_OR_MISMATCHED_PASSWORD);
        }
    }

    private void validateNotSameAsCurrentPassword(User user, String rawNewPassword) {
        if (user.getPassword() != null && passwordEncoder.matches(rawNewPassword, user.getPassword())) {
            throw new AppException(IamPasswordErrorCode.SAME_AS_CURRENT_PASSWORD);
        }
    }
}
