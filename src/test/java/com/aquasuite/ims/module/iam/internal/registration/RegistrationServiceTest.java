package com.aquasuite.ims.module.iam.internal.registration;

import com.aquasuite.ims.module.crm.api.CustomerRegistrationApi;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.payload.request.*;
import com.aquasuite.ims.module.iam.internal.payload.response.LoginResponse;
import com.aquasuite.ims.module.iam.internal.repository.*;
import com.aquasuite.ims.module.iam.internal.service.*;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
    private static final String EMAIL = "customer@example.test";
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder encoder;
    @Mock RegistrationOtpStore store;
    @Mock JavaMailSender mail;
    @Mock PlatformTransactionManager manager;
    @Mock AuthService auth;
    @Mock CustomerRegistrationApi profiles;
    RegisterService service;
    PendingRegistration pending;
    RegistrationOtpStore.Claim claim;

    @BeforeEach
    void setup() {
        service = new RegisterService(users, roles, encoder, store, mail,
                new TransactionTemplate(manager), auth, profiles);
        pending = new PendingRegistration(UUID.randomUUID(), "Customer Name", EMAIL,
                "0901234567", "encoded-password");
        claim = new RegistrationOtpStore.Claim(pending, "version", "claim");
    }

    private VerifyRegistrationRequest verification() {
        return new VerifyRegistrationRequest(EMAIL, "012345");
    }

    private void claimed() {
        when(store.verifyAndClaim(EMAIL, "012345")).thenReturn(claim);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private void creatable() {
        claimed();
        Role customer = new Role();
        customer.setActive(true);
        customer.setRoleName("CUSTOMER");
        when(roles.findByRoleNameIgnoreCase("CUSTOMER")).thenReturn(Optional.of(customer));
        when(users.insertPendingRegistration(any(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(1);
        User user = new User();
        user.setId(pending.id());
        user.setEmail(EMAIL);
        user.setPhone(pending.phone());
        user.setFullName(pending.fullName());
        when(users.findById(pending.id())).thenReturn(Optional.of(user));
    }

    @Test
    void registrationStoresOnlyHashesInRedisAndDoesNotWriteDatabase() {
        when(encoder.encode(anyString())).thenReturn("encoded-value");
        var response = service.register(new RegisterRequest("Customer Name", EMAIL,
                "0901234567", "SyntheticPass1!", "SyntheticPass1!", true));
        var captured = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(store).save(captured.capture(), eq("encoded-value"));
        assertThat(captured.getValue().passwordHash()).isEqualTo("encoded-value");
        assertThat(response.id()).isEqualTo(captured.getValue().id());
        verify(users, never()).saveAndFlush(any());
        verify(users, never()).insertPendingRegistration(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(roles, manager, profiles, auth);
        verify(mail).send(any(SimpleMailMessage.class));
        assertThat(captured.getValue().toString()).doesNotContain("encoded-value");
    }

    @Test
    void duplicateAccountDoesNotCreatePendingStateOrSendEmail() {
        when(users.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);
        assertCode(() -> service.register(new RegisterRequest("Customer Name", EMAIL,
                "0901234567", "SyntheticPass1!", "SyntheticPass1!", true)), "REG_001");
        verifyNoInteractions(store, encoder, mail);
    }

    @Test
    void mailFailureRetainsPendingStateWithoutCreatingUser() {
        when(encoder.encode(anyString())).thenReturn("encoded-value");
        doThrow(new MailSendException("synthetic outage")).when(mail).send(any(SimpleMailMessage.class));
        assertCode(() -> service.register(new RegisterRequest("Customer Name", EMAIL,
                "0901234567", "SyntheticPass1!", "SyntheticPass1!", true)), "REG_007");
        verify(store).save(any(), anyString());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void validOtpCreatesAccountAndProfileBeforeCommitThenCompletesAndLogsIn() {
        creatable();
        LoginResponse response = LoginResponse.builder().build();
        when(auth.loginRegisteredUser(pending.id())).thenReturn(response);
        assertThat(service.verify(verification())).isSameAs(response);
        var order = inOrder(store, users, profiles, manager, auth);
        order.verify(store).verifyAndClaim(EMAIL, "012345");
        order.verify(users).saveAndFlush(argThat(u -> u.getStatus() == UserStatus.ACTIVE
                && u.isVerified() && u.getRoles().size() == 1));
        order.verify(profiles).createProfile(argThat(r -> r.userId().equals(pending.id())));
        order.verify(manager).commit(any());
        order.verify(store).complete(claim);
        order.verify(auth).loginRegisteredUser(pending.id());
    }

    @Test
    void crmFailureRollsBackAndReleasesOtpWithoutStartingSession() {
        creatable();
        doThrow(new IllegalStateException("synthetic CRM failure")).when(profiles).createProfile(any());
        assertCode(() -> service.verify(verification()), "REG_008");
        verify(manager).rollback(any());
        verify(store).release(claim);
        verify(store, never()).complete(any());
        verifyNoInteractions(auth);
    }

    @Test
    void inactiveOrMissingRoleRollsBackAndReleasesOtp() {
        claimed();
        when(roles.findByRoleNameIgnoreCase("CUSTOMER")).thenReturn(Optional.empty());
        assertCode(() -> service.verify(verification()), "REG_008");
        verify(manager).rollback(any());
        verify(store).release(claim);
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(auth, profiles);
    }

    @Test
    void duplicateAppearingAfterClaimPreventsCreation() {
        claimed();
        when(users.existsByEmailIgnoreCase(EMAIL)).thenReturn(false, true);
        assertCode(() -> service.verify(verification()), "REG_001");
        verify(store).release(claim);
        verify(manager).rollback(any());
        verifyNoInteractions(profiles, auth);
    }

    @Test
    void sessionFailureLeavesCommittedAccountAndReturnsLoginRequired() {
        creatable();
        doThrow(new IllegalStateException("synthetic session failure"))
                .when(auth).loginRegisteredUser(pending.id());
        assertCode(() -> service.verify(verification()), "REG_009");
        verify(manager).commit(any());
        verify(manager, never()).rollback(any());
        verify(store).complete(claim);
        verify(store, never()).release(any());
    }

    @Test
    void cleanupFailureAfterCommitDoesNotPreventLogin() {
        creatable();
        doThrow(new IllegalStateException("synthetic Redis outage")).when(store).complete(claim);
        service.verify(verification());
        verify(manager).commit(any());
        verify(auth).loginRegisteredUser(pending.id());
    }

    @Test
    void resendRotatesBeforeSendingEmailWithoutDatabaseWrites() {
        when(encoder.encode(anyString())).thenReturn("encoded-otp");
        service.resend(new ResendRegistrationOtpRequest(EMAIL));
        var order = inOrder(store, mail);
        order.verify(store).rotateOtp(EMAIL, "encoded-otp");
        order.verify(mail).send(any(SimpleMailMessage.class));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(manager, profiles, auth);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }
}
