package com.aquasuite.ims.service.iam;

import com.aquasuite.ims.event.iam.PasswordChangedEvent;
import com.aquasuite.ims.entity.iam.User;
import com.aquasuite.ims.enums.iam.UserStatus;
import com.aquasuite.ims.exception.iam.IamPasswordErrorCode;
import com.aquasuite.ims.dto.iam.request.ChangePasswordRequest;
import com.aquasuite.ims.dto.iam.request.ForgotPasswordRequest;
import com.aquasuite.ims.enums.iam.RecoveryChannel;
import com.aquasuite.ims.dto.iam.request.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.dto.iam.response.PasswordActionResponse;
import com.aquasuite.ims.repository.iam.UserRepository;
import com.aquasuite.ims.service.iam.impl.AuthServiceImpl;
import com.aquasuite.ims.integration.iam.EmailOtpSender;
import com.aquasuite.ims.integration.iam.OtpSenderStrategy;
import com.aquasuite.ims.integration.iam.SmsOtpSender;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private AuthServiceImpl passwordService;

    private final String RAW_OLD_PWD = "OldPassword@123";
    private final String RAW_NEW_PWD = "NewPassword@456";

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        lenient().when(emailOtpSender.getSupportedChannel()).thenReturn(RecoveryChannel.EMAIL);
        SmsOtpSender smsOtpSender = new SmsOtpSender();
        List<OtpSenderStrategy> strategies = List.of(emailOtpSender, smsOtpSender);

        passwordService = new AuthServiceImpl(
                null, userRepository, null, null, null, null, passwordEncoder, null, null, null,
                strategies, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(UUID userId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Change password successfully: updates password, publishes event, returns MSG18")
    void testChangePassword_Success() {
        UUID userId = UUID.randomUUID();
        authenticateUser(userId);

        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setPasswordHash(passwordEncoder.encode(RAW_OLD_PWD));
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest(RAW_OLD_PWD, RAW_NEW_PWD, RAW_NEW_PWD);
        PasswordActionResponse response = passwordService.changePassword(request);

        assertThat(response.message()).isEqualTo("Password changed successfully. Please log in again if required.");
        assertThat(passwordEncoder.matches(RAW_NEW_PWD, user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordLastChangedAt()).isNotNull();

        verify(userRepository).save(user);
        verify(eventPublisher).publishEvent(any(PasswordChangedEvent.class));
    }

    @Test
    @DisplayName("Change password fails when user is unauthenticated")
    void testChangePassword_Unauthenticated() {
        SecurityContextHolder.clearContext();

        ChangePasswordRequest request = new ChangePasswordRequest(RAW_OLD_PWD, RAW_NEW_PWD, RAW_NEW_PWD);

        assertThatThrownBy(() -> passwordService.changePassword(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.INCORRECT_CURRENT_PASSWORD));
    }

    @Test
    @DisplayName("Change password fails when current password is incorrect (MSG21)")
    void testChangePassword_IncorrectCurrentPassword() {
        UUID userId = UUID.randomUUID();
        authenticateUser(userId);

        User user = new User();
        user.setId(userId);
        user.setPasswordHash(passwordEncoder.encode(RAW_OLD_PWD));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword@123", RAW_NEW_PWD, RAW_NEW_PWD);

        assertThatThrownBy(() -> passwordService.changePassword(request))
                .isInstanceOf(AppException.class)
                .hasMessage(IamPasswordErrorCode.INCORRECT_CURRENT_PASSWORD.getMessage())
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.INCORRECT_CURRENT_PASSWORD));
    }

    @Test
    @DisplayName("Change password fails when new password is weak or does not match confirmation (MSG22)")
    void testChangePassword_WeakOrMismatched() {
        UUID userId = UUID.randomUUID();
        authenticateUser(userId);

        User user = new User();
        user.setId(userId);
        user.setPasswordHash(passwordEncoder.encode(RAW_OLD_PWD));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

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
        UUID userId = UUID.randomUUID();
        authenticateUser(userId);

        User user = new User();
        user.setId(userId);
        user.setPasswordHash(passwordEncoder.encode(RAW_OLD_PWD));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ChangePasswordRequest reqCurrent = new ChangePasswordRequest(RAW_OLD_PWD, RAW_OLD_PWD, RAW_OLD_PWD);
        assertThatThrownBy(() -> passwordService.changePassword(reqCurrent))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.SAME_AS_CURRENT_PASSWORD));
    }

    @Test
    @DisplayName("Request password recovery via Email sends 6-digit OTP and returns MSG19")
    void testRequestPasswordRecovery_Email_Success() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setEmailSentCount(0);

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        ForgotPasswordRequest req = new ForgotPasswordRequest("user@example.com", RecoveryChannel.EMAIL);
        PasswordActionResponse response = passwordService.requestPasswordRecovery(req);

        assertThat(response.message()).isEqualTo("A password reset code has been sent to your email.");
        assertThat(user.getVerificationCode()).isNotNull();
        assertThat(user.getVerificationExpiredAt()).isAfter(Instant.now().plus(9, ChronoUnit.MINUTES));
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
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setEmailSentDate(LocalDate.now());
        user.setEmailSentCount(5);

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        ForgotPasswordRequest req = new ForgotPasswordRequest("user@example.com", RecoveryChannel.EMAIL);
        assertThatThrownBy(() -> passwordService.requestPasswordRecovery(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.EMAIL_RATE_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("Request password recovery fails for SMS channel (not supported yet)")
    void testRequestPasswordRecovery_SmsNotSupported() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPhone("0912345678");

        when(userRepository.findByPhone("0912345678")).thenReturn(Optional.of(user));

        ForgotPasswordRequest req = new ForgotPasswordRequest("0912345678", RecoveryChannel.SMS);
        assertThatThrownBy(() -> passwordService.requestPasswordRecovery(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.SMS_CHANNEL_NOT_SUPPORTED_YET));
    }

    @Test
    @DisplayName("Request password recovery fails when user is not found")
    void testRequestPasswordRecovery_UserNotFound() {
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        ForgotPasswordRequest req = new ForgotPasswordRequest("unknown@example.com", RecoveryChannel.EMAIL);
        assertThatThrownBy(() -> passwordService.requestPasswordRecovery(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("Reset password with valid OTP resets password, unlocks inactive account, clears OTP, returns MSG20")
    void testResetPasswordWithOtp_Success() {
        String rawOtp = "123456";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash(passwordEncoder.encode(RAW_OLD_PWD));
        user.setVerificationCode(passwordEncoder.encode(rawOtp));
        user.setVerificationExpiredAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        user.setStatus(UserStatus.INACTIVE);

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        ResetPasswordWithOtpRequest req = new ResetPasswordWithOtpRequest(
                "user@example.com",
                rawOtp,
                RAW_NEW_PWD,
                RAW_NEW_PWD
        );

        PasswordActionResponse response = passwordService.resetPasswordWithOtp(req);

        assertThat(response.message()).isEqualTo("Your password has been reset successfully. You may now sign in.");
        assertThat(passwordEncoder.matches(RAW_NEW_PWD, user.getPasswordHash())).isTrue();
        assertThat(user.getVerificationCode()).isNull();
        assertThat(user.getVerificationExpiredAt()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE); // Unlocked!

        verify(userRepository).save(user);
        verify(eventPublisher).publishEvent(any(PasswordChangedEvent.class));
    }

    @Test
    @DisplayName("Reset password fails when OTP is invalid or expired")
    void testResetPasswordWithOtp_InvalidOrExpired() {
        User expiredUser = new User();
        expiredUser.setId(UUID.randomUUID());
        expiredUser.setEmail("user@example.com");
        expiredUser.setVerificationCode(passwordEncoder.encode("123456"));
        expiredUser.setVerificationExpiredAt(Instant.now().minus(1, ChronoUnit.MINUTES));

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
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash(passwordEncoder.encode(RAW_OLD_PWD));
        user.setVerificationCode(passwordEncoder.encode(rawOtp));
        user.setVerificationExpiredAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        user.setStatus(UserStatus.ACTIVE);

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

    @Test
    @DisplayName("Change password fails when authenticated user ID does not exist in repository")
    void testChangePassword_UserNotFound() {
        UUID missingUserId = UUID.randomUUID();
        authenticateUser(missingUserId);
        when(userRepository.findById(missingUserId)).thenReturn(Optional.empty());

        ChangePasswordRequest req = new ChangePasswordRequest(RAW_OLD_PWD, RAW_NEW_PWD, RAW_NEW_PWD);
        assertThatThrownBy(() -> passwordService.changePassword(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.USER_NOT_FOUND));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Change password fails when stored passwordHash is null")
    void testChangePassword_NullPasswordHash() {
        UUID userId = UUID.randomUUID();
        authenticateUser(userId);
        User user = new User();
        user.setId(userId);
        user.setPasswordHash(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ChangePasswordRequest req = new ChangePasswordRequest(RAW_OLD_PWD, RAW_NEW_PWD, RAW_NEW_PWD);
        assertThatThrownBy(() -> passwordService.changePassword(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.INCORRECT_CURRENT_PASSWORD));
    }

    @Test
    @DisplayName("Change password rejects whitespace or passwords exceeding 72 UTF-8 bytes")
    void testChangePassword_WhitespaceOrOver72Bytes() {
        UUID userId = UUID.randomUUID();
        authenticateUser(userId);
        User user = new User();
        user.setId(userId);
        user.setPasswordHash(passwordEncoder.encode(RAW_OLD_PWD));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ChangePasswordRequest withSpace = new ChangePasswordRequest(RAW_OLD_PWD, "New Pass@456", "New Pass@456");
        assertThatThrownBy(() -> passwordService.changePassword(withSpace))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.WEAK_OR_MISMATCHED_PASSWORD));

        String over72Bytes = "Aa1@" + "x".repeat(69);
        ChangePasswordRequest tooLong = new ChangePasswordRequest(RAW_OLD_PWD, over72Bytes, over72Bytes);
        assertThatThrownBy(() -> passwordService.changePassword(tooLong))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.WEAK_OR_MISMATCHED_PASSWORD));
    }

    @Test
    @DisplayName("Request password recovery resets daily counter on a new day")
    void testRequestPasswordRecovery_ResetsDailyCounterOnNewDay() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setEmailSentDate(LocalDate.now().minusDays(1));
        user.setEmailSentCount(5);

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        PasswordActionResponse response = passwordService.requestPasswordRecovery(
                new ForgotPasswordRequest("user@example.com", RecoveryChannel.EMAIL));

        assertThat(response.message()).isEqualTo("A password reset code has been sent to your email.");
        assertThat(user.getEmailSentDate()).isEqualTo(LocalDate.now());
        assertThat(user.getEmailSentCount()).isEqualTo(1);
        verify(emailOtpSender).sendRecoveryOtp(eq("user@example.com"), anyString());
    }

    @Test
    @DisplayName("Request password recovery via SMS fails when phone does not belong to any user")
    void testRequestPasswordRecovery_SmsUserNotFound() {
        when(userRepository.findByPhone("0999888777")).thenReturn(Optional.empty());

        ForgotPasswordRequest req = new ForgotPasswordRequest("0999888777", RecoveryChannel.SMS);
        assertThatThrownBy(() -> passwordService.requestPasswordRecovery(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("Reset password with OTP accepts phone identifier fallback when email lookup is empty")
    void testResetPasswordWithOtp_PhoneIdentifierSuccess() {
        String rawOtp = "654321";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPhone("0909123456");
        user.setPasswordHash(passwordEncoder.encode(RAW_OLD_PWD));
        user.setVerificationCode(passwordEncoder.encode(rawOtp));
        user.setVerificationExpiredAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmailIgnoreCase("0909123456")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("0909123456")).thenReturn(Optional.of(user));

        ResetPasswordWithOtpRequest req = new ResetPasswordWithOtpRequest(
                "0909123456", rawOtp, RAW_NEW_PWD, RAW_NEW_PWD);
        PasswordActionResponse res = passwordService.resetPasswordWithOtp(req);

        assertThat(res.message()).contains("reset successfully");
        assertThat(passwordEncoder.matches(RAW_NEW_PWD, user.getPasswordHash())).isTrue();
        assertThat(user.getVerificationCode()).isNull();
        verify(eventPublisher).publishEvent(any(PasswordChangedEvent.class));
    }

    @Test
    @DisplayName("Reset password fails when OTP code does not match stored hash")
    void testResetPasswordWithOtp_WrongOtpCode() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setVerificationCode(passwordEncoder.encode("123456"));
        user.setVerificationExpiredAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        ResetPasswordWithOtpRequest req = new ResetPasswordWithOtpRequest(
                "user@example.com", "999999", RAW_NEW_PWD, RAW_NEW_PWD);
        assertThatThrownBy(() -> passwordService.resetPasswordWithOtp(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.INVALID_OR_EXPIRED_OTP));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reset password fails when user is not found by email or phone")
    void testResetPasswordWithOtp_UserNotFound() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("missing@example.com")).thenReturn(Optional.empty());

        ResetPasswordWithOtpRequest req = new ResetPasswordWithOtpRequest(
                "missing@example.com", "123456", RAW_NEW_PWD, RAW_NEW_PWD);
        assertThatThrownBy(() -> passwordService.resetPasswordWithOtp(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("Reset password rejects weak new password even when OTP is valid")
    void testResetPasswordWithOtp_WeakPassword() {
        String rawOtp = "123456";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash(passwordEncoder.encode(RAW_OLD_PWD));
        user.setVerificationCode(passwordEncoder.encode(rawOtp));
        user.setVerificationExpiredAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        ResetPasswordWithOtpRequest req = new ResetPasswordWithOtpRequest(
                "user@example.com", rawOtp, "weak", "weak");
        assertThatThrownBy(() -> passwordService.resetPasswordWithOtp(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamPasswordErrorCode.WEAK_OR_MISMATCHED_PASSWORD));
    }
}
