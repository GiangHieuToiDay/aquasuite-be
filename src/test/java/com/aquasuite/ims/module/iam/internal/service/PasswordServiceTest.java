package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.api.IamFacade;
import com.aquasuite.ims.module.iam.events.PasswordChangedEvent;
import com.aquasuite.ims.module.iam.internal.entity.UserPasswordRecovery;
import com.aquasuite.ims.module.iam.internal.entity.enums.RecoveryChannel;
import com.aquasuite.ims.module.iam.internal.exception.IamPasswordErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.*;
import com.aquasuite.ims.module.iam.internal.repository.UserPasswordRecoveryRepository;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.service.impl.PasswordServiceImpl;
import com.aquasuite.ims.module.iam.internal.service.otp.*;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordServiceTest {
    final UUID id = UUID.randomUUID();
    final String oldPassword = "OldPassword@123", newPassword = "NewPassword@456";
    final UserRepository users = mock(UserRepository.class);
    final UserPasswordRecoveryRepository recoveries = mock(UserPasswordRecoveryRepository.class);
    final IamFacade actors = mock(IamFacade.class);
    final EmailOtpSender email = mock(EmailOtpSender.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final UserPasswordRecovery recovery = new UserPasswordRecovery();
    User user;
    PasswordServiceImpl service;

    @BeforeEach
    void setup() {
        user =
                User.builder()
                        .id(id)
                        .email("user@example.com")
                        .status(UserStatus.ACTIVE)
                        .passwordHash(encoder.encode(oldPassword))
                        .build();
        recovery.setUserId(id);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(recoveries.findById(id)).thenReturn(Optional.of(recovery));
        when(actors.getCurrentUserId()).thenReturn(id);
        when(email.getSupportedChannel()).thenReturn(RecoveryChannel.EMAIL);
        service =
                new PasswordServiceImpl(
                        users,
                        recoveries,
                        actors,
                        encoder,
                        List.of(email, new SmsOtpSender()),
                        events);
    }

    ChangePasswordRequest change(String current, String next) {
        return new ChangePasswordRequest(current, next, next);
    }

    ResetPasswordWithOtpRequest reset(String next) {
        return new ResetPasswordWithOtpRequest(user.getEmail(), "123456", next, next);
    }

    void validOtp() {
        recovery.setVerificationCode(encoder.encode("123456"));
        recovery.setVerificationExpiredAt(LocalDateTime.now().plusMinutes(5));
    }

    @Test
    void changePasswordUsesAuthenticatedUuidAndNewAuthenticationHash() {
        service.changePassword(change(oldPassword, newPassword));
        assertThat(encoder.matches(newPassword, user.getPasswordHash())).isTrue();
        assertThat(recovery.getPasswordLastChangedAt()).isNotNull();
        verify(users).findById(id);
        verify(events).publishEvent(new PasswordChangedEvent(id, "CHANGE_PASSWORD"));
    }

    @Test
    void wrongCurrentPasswordDoesNotWrite() {
        assertThatThrownBy(() -> service.changePassword(change("wrong", newPassword)))
                .isInstanceOf(AppException.class);
        verify(users, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void weakPasswordIsRejected() {
        assertThatThrownBy(() -> service.changePassword(change(oldPassword, "weak")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void samePasswordIsRejected() {
        assertThatThrownBy(() -> service.changePassword(change(oldPassword, oldPassword)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void emailRecoveryHashesOtpAndRetainsRateLimit() {
        service.requestPasswordRecovery(
                new ForgotPasswordRequest(user.getEmail(), RecoveryChannel.EMAIL));
        var sent = ArgumentCaptor.forClass(String.class);
        verify(email).sendRecoveryOtp(eq(user.getEmail()), sent.capture());
        assertThat(sent.getValue()).matches("[0-9]{6}");
        assertThat(encoder.matches(sent.getValue(), recovery.getVerificationCode())).isTrue();
        assertThat(recovery.getEmailSentCount()).isEqualTo(1);
        assertThat(recovery.getEmailSentDate()).isEqualTo(LocalDate.now());
        assertThat(recovery.getVerificationExpiredAt()).isAfter(LocalDateTime.now().plusMinutes(9));
        verify(recoveries).save(recovery);
    }

    @Test
    void dailyRecoveryLimitStillApplies() {
        recovery.setEmailSentDate(LocalDate.now());
        recovery.setEmailSentCount(5);
        assertThatThrownBy(
                        () ->
                                service.requestPasswordRecovery(
                                        new ForgotPasswordRequest(
                                                user.getEmail(), RecoveryChannel.EMAIL)))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(IamPasswordErrorCode.EMAIL_RATE_LIMIT_EXCEEDED));
        verify(email, never()).sendRecoveryOtp(any(), any());
    }

    @Test
    void unsupportedSmsIsRejected() {
        when(users.findByPhone("0912345678")).thenReturn(Optional.of(user));
        assertThatThrownBy(
                        () ->
                                service.requestPasswordRecovery(
                                        new ForgotPasswordRequest(
                                                "0912345678", RecoveryChannel.SMS)))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(
                                                IamPasswordErrorCode
                                                        .SMS_CHANNEL_NOT_SUPPORTED_YET));
    }

    @Test
    void resetChangesAuthPasswordClearsOtpAndPublishesUuidEvent() {
        validOtp();
        user.setStatus(UserStatus.INACTIVE);
        service.resetPasswordWithOtp(reset(newPassword));
        assertThat(encoder.matches(newPassword, user.getPasswordHash())).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(recovery.getVerificationCode()).isNull();
        assertThat(recovery.getVerificationExpiredAt()).isNull();
        verify(events).publishEvent(new PasswordChangedEvent(id, "FORGOT_PASSWORD_RESET"));
    }

    @Test
    void expiredOtpIsRejected() {
        validOtp();
        recovery.setVerificationExpiredAt(LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> service.resetPasswordWithOtp(reset(newPassword)))
                .isInstanceOf(AppException.class);
        verify(users, never()).save(any());
    }

    @Test
    void resetCannotReuseCurrentPassword() {
        validOtp();
        assertThatThrownBy(() -> service.resetPasswordWithOtp(reset(oldPassword)))
                .isInstanceOf(AppException.class);
    }
}
