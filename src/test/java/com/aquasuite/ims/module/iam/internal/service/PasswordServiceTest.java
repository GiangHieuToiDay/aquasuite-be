package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.events.PasswordChangedEvent;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.enums.RecoveryChannel;
import com.aquasuite.ims.module.iam.internal.entity.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.exception.IamPasswordErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.ChangePasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.ForgotPasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.PasswordActionResponse;
import com.aquasuite.ims.module.iam.internal.payload.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.service.impl.PasswordServiceImpl;
import com.aquasuite.ims.module.iam.internal.service.otp.EmailOtpSender;
import com.aquasuite.ims.module.iam.internal.service.otp.OtpSenderStrategy;
import com.aquasuite.ims.module.iam.internal.service.otp.SmsOtpSender;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EmailOtpSender emailOtpSender;

    private PasswordEncoder passwordEncoder;
    private PasswordServiceImpl passwordService;

    private final String RAW_OLD_PWD = "OldPassword@123";
    private final String RAW_NEW_PWD = "NewPassword@456";

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        lenient().when(emailOtpSender.getSupportedChannel()).thenReturn(RecoveryChannel.EMAIL);
        SmsOtpSender smsOtpSender = new SmsOtpSender();
        List<OtpSenderStrategy> strategies = List.of(emailOtpSender, smsOtpSender);

        passwordService = new PasswordServiceImpl(
                userRepository,
                passwordEncoder,
                strategies,
                eventPublisher
        );
    }

    @Test
    @DisplayName("Change password successfully: updates password, publishes event, returns MSG18")
    void testChangePassword_Success() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .password(passwordEncoder.encode(RAW_OLD_PWD))
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest(RAW_OLD_PWD, RAW_NEW_PWD, RAW_NEW_PWD);
        PasswordActionResponse response = passwordService.changePassword(request);

        assertThat(response.message()).isEqualTo("Password changed successfully. Please log in again if required.");
        assertThat(passwordEncoder.matches(RAW_NEW_PWD, user.getPassword())).isTrue();
        assertThat(user.getPasswordLastChangedAt()).isNotNull();

        verify(eventPublisher).publishEvent(any(PasswordChangedEvent.class));
    }

    @Test
    @DisplayName("Change password fails when current password is incorrect (MSG21)")
    void testChangePassword_IncorrectCurrentPassword() {
        User user = User.builder()
                .id(1L)
                .password(passwordEncoder.encode(RAW_OLD_PWD))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword@123", RAW_NEW_PWD, RAW_NEW_PWD);

        assertThatThrownBy(() -> passwordService.changePassword(request))
                .isInstanceOf(AppException.class)
                .hasMessage(IamPasswordErrorCode.INCORRECT_CURRENT_PASSWORD.getMessage())
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.INCORRECT_CURRENT_PASSWORD));
    }

    @Test
    @DisplayName("Change password fails when new password is weak or does not match confirmation (MSG22)")
    void testChangePassword_WeakOrMismatched() {
        User user = User.builder()
                .id(1L)
                .password(passwordEncoder.encode(RAW_OLD_PWD))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Mismatched confirmation
        ChangePasswordRequest mismatch = new ChangePasswordRequest(RAW_OLD_PWD, RAW_NEW_PWD, "Different@456");
        assertThatThrownBy(() -> passwordService.changePassword(mismatch))
                .isInstanceOf(AppException.class)
                .hasMessage(IamPasswordErrorCode.WEAK_OR_MISMATCHED_PASSWORD.getMessage());

        // Weak: missing special character
        ChangePasswordRequest weak = new ChangePasswordRequest(RAW_OLD_PWD, "WeakPassword123", "WeakPassword123");
        assertThatThrownBy(() -> passwordService.changePassword(weak))
                .isInstanceOf(AppException.class)
                .hasMessage(IamPasswordErrorCode.WEAK_OR_MISMATCHED_PASSWORD.getMessage());
    }

    @Test
    @DisplayName("Change password fails when new password is the same as current password")
    void testChangePassword_SameAsCurrentPassword() {
        User user = User.builder()
                .id(1L)
                .password(passwordEncoder.encode(RAW_OLD_PWD))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ChangePasswordRequest reqCurrent = new ChangePasswordRequest(RAW_OLD_PWD, RAW_OLD_PWD, RAW_OLD_PWD);
        assertThatThrownBy(() -> passwordService.changePassword(reqCurrent))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.SAME_AS_CURRENT_PASSWORD));
    }

    @Test
    @DisplayName("Request password recovery via Email sends 6-digit OTP and returns MSG19")
    void testRequestPasswordRecovery_Email_Success() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .emailSentCount(0)
                .build();

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        ForgotPasswordRequest req = new ForgotPasswordRequest("user@example.com", RecoveryChannel.EMAIL);
        PasswordActionResponse response = passwordService.requestPasswordRecovery(req);

        assertThat(response.message()).isEqualTo("A password reset code has been sent to your email.");
        assertThat(user.getVerificationCode()).isNotNull();
        assertThat(user.getVerificationExpiredAt()).isAfter(LocalDateTime.now().plusMinutes(9));
        assertThat(user.getEmailSentCount()).isEqualTo(1);
        assertThat(user.getEmailSentDate()).isEqualTo(LocalDate.now());

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailOtpSender).sendRecoveryOtp(eq("user@example.com"), otpCaptor.capture());
        assertThat(otpCaptor.getValue()).matches("^\\d{6}$");
        assertThat(passwordEncoder.matches(otpCaptor.getValue(), user.getVerificationCode())).isTrue();
    }

    @Test
    @DisplayName("Request password recovery fails when rate limit exceeded (5 requests/day)")
    void testRequestPasswordRecovery_RateLimitExceeded() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .emailSentDate(LocalDate.now())
                .emailSentCount(5)
                .build();

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        ForgotPasswordRequest req = new ForgotPasswordRequest("user@example.com", RecoveryChannel.EMAIL);
        assertThatThrownBy(() -> passwordService.requestPasswordRecovery(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.EMAIL_RATE_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("Request password recovery fails for SMS channel (not supported yet)")
    void testRequestPasswordRecovery_SmsNotSupported() {
        User user = User.builder()
                .id(1L)
                .phone("0912345678")
                .build();

        when(userRepository.findByPhone("0912345678")).thenReturn(Optional.of(user));

        ForgotPasswordRequest req = new ForgotPasswordRequest("0912345678", RecoveryChannel.SMS);
        assertThatThrownBy(() -> passwordService.requestPasswordRecovery(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.SMS_CHANNEL_NOT_SUPPORTED_YET));
    }

    @Test
    @DisplayName("Reset password with valid OTP resets password, unlocks inactive account, clears OTP, returns MSG20")
    void testResetPasswordWithOtp_Success() {
        String rawOtp = "123456";
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .password(passwordEncoder.encode(RAW_OLD_PWD))
                .verificationCode(passwordEncoder.encode(rawOtp))
                .verificationExpiredAt(LocalDateTime.now().plusMinutes(5))
                .status(UserStatus.INACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        ResetPasswordWithOtpRequest req = new ResetPasswordWithOtpRequest(
                "user@example.com",
                rawOtp,
                RAW_NEW_PWD,
                RAW_NEW_PWD
        );

        PasswordActionResponse response = passwordService.resetPasswordWithOtp(req);

        assertThat(response.message()).isEqualTo("Your password has been reset successfully. You may now sign in.");
        assertThat(passwordEncoder.matches(RAW_NEW_PWD, user.getPassword())).isTrue();
        assertThat(user.getVerificationCode()).isNull();
        assertThat(user.getVerificationExpiredAt()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE); // Unlocked!

        verify(eventPublisher).publishEvent(any(PasswordChangedEvent.class));
    }

    @Test
    @DisplayName("Reset password fails when OTP is invalid or expired")
    void testResetPasswordWithOtp_InvalidOrExpired() {
        User expiredUser = User.builder()
                .id(1L)
                .email("user@example.com")
                .verificationCode(passwordEncoder.encode("123456"))
                .verificationExpiredAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(expiredUser));

        ResetPasswordWithOtpRequest req = new ResetPasswordWithOtpRequest(
                "user@example.com",
                "123456",
                RAW_NEW_PWD,
                RAW_NEW_PWD
        );

        assertThatThrownBy(() -> passwordService.resetPasswordWithOtp(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.INVALID_OR_EXPIRED_OTP));
    }

    @Test
    @DisplayName("Reset password fails when new password matches current password")
    void testResetPasswordWithOtp_SameAsCurrentPassword() {
        String rawOtp = "123456";
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .password(passwordEncoder.encode(RAW_OLD_PWD))
                .verificationCode(passwordEncoder.encode(rawOtp))
                .verificationExpiredAt(LocalDateTime.now().plusMinutes(5))
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        ResetPasswordWithOtpRequest req = new ResetPasswordWithOtpRequest(
                "user@example.com",
                rawOtp,
                RAW_OLD_PWD,
                RAW_OLD_PWD
        );

        assertThatThrownBy(() -> passwordService.resetPasswordWithOtp(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.SAME_AS_CURRENT_PASSWORD));
    }
}
