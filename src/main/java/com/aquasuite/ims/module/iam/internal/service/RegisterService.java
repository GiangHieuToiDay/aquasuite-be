package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.error.RegistrationErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.request.RegisterRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.ResendRegistrationOtpRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.VerifyRegistrationRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.LoginResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.RegisterResponse;
import com.aquasuite.ims.module.crm.api.CustomerRegistrationApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerRegistrationRequest;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CUSTOMER account registration and email OTP activation; User is the IAM identity entity. */
@Service
@Validated
@RequiredArgsConstructor
public class RegisterService {
    public static final String OTP_PREFIX = "iam:registration:otp:";
    static final long OTP_TTL_MS = 300_000;
    static final long RESEND_DELAY_MS = 60_000;
    static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final DefaultRedisScript<Long> STORE =
            new DefaultRedisScript<>(
                    """
            redis.call('HSET', KEYS[1], 'hash', ARGV[1], 'sentAt', ARGV[2],
                       'attempts', ARGV[3], 'version', ARGV[4])
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            return 1
            """,
                    Long.class);
    private static final DefaultRedisScript<Long> ATTEMPT =
            new DefaultRedisScript<>(
                    """
            if redis.call('HGET', KEYS[1], 'version') ~= ARGV[1]
               or redis.call('PTTL', KEYS[1]) <= 0 then return -1 end
            return redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            """,
                    Long.class);
    private static final DefaultRedisScript<Long> CONSUME =
            new DefaultRedisScript<>(
                    """
            if redis.call('HGET', KEYS[1], 'version') ~= ARGV[1]
               or redis.call('PTTL', KEYS[1]) <= 0 then return 0 end
            return redis.call('DEL', KEYS[1])
            """,
                    Long.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final StringRedisTemplate redis;
    private final JavaMailSender mail;
    private final TransactionTemplate transactions;
    private final AuthService auth;
    private final CustomerRegistrationApi customerProfiles;

    /** Persists a pending account before email delivery; duplicate accounts are never overwritten. */
    public RegisterResponse register(@Valid RegisterRequest request) {
        RegisterResponse response;
        try {
            response =
                    transactions.execute(
                            tx -> {
                                if (users.existsByEmailIgnoreCase(request.email())
                                        || users.existsByPhone(request.phone())) {
                                    throw new AppException(RegistrationErrorCode.DUPLICATE_ACCOUNT);
                                }
                                var role =
                                        roles.findByRoleNameIgnoreCase("CUSTOMER")
                                                .filter(r -> r.isActive())
                                                .orElseThrow(
                                                        () ->
                                                                new AppException(
                                                                        RegistrationErrorCode
                                                                                .UNAVAILABLE));
                                UUID id = UUID.randomUUID();
                                int inserted =
                                        users.insertPendingRegistration(
                                                id,
                                                "customer-" + id,
                                                request.fullName(),
                                                request.email(),
                                                request.phone(),
                                                encoder.encode(request.password()));
                                if (inserted != 1) {
                                    if (users.existsByEmailIgnoreCase(request.email())
                                            || users.existsByPhone(request.phone())) {
                                        throw new AppException(
                                                RegistrationErrorCode.DUPLICATE_ACCOUNT);
                                    }
                                    throw new AppException(
                                            CommonErrorCode.DATA_INTEGRITY_VIOLATION);
                                }
                                User user =
                                        users.findById(id)
                                                .orElseThrow(
                                                        () ->
                                                                new AppException(
                                                                        RegistrationErrorCode
                                                                                .UNAVAILABLE));
                                user.getRoles().add(role);
                                users.saveAndFlush(user);
                                return new RegisterResponse(
                                        id, user.getEmail(), user.getStatus().name());
                            });
        } catch (DataIntegrityViolationException ex) {
            // The transaction has already rolled back; do not continue inside a failed transaction.
            String constraint = constraintName(ex);
            if ("uq_users_email_lower".equals(constraint)
                    || "uq_users_phone_not_null".equals(constraint)) {
                throw new AppException(RegistrationErrorCode.DUPLICATE_ACCOUNT);
            }
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        } catch (DataAccessException | org.springframework.transaction.TransactionException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        resend(new ResendRegistrationOtpRequest(request.email()));
        return response;
    }

    /** Serializes OTP rotation with verification, then sends mail outside the database lock. */
    public void resend(@Valid ResendRegistrationOtpRequest request) {
        OtpDelivery delivery;
        try {
            delivery =
                    transactions.execute(
                            tx -> {
                                User user = pendingUser(request.email());
                                String key = OTP_PREFIX + user.getId();
                                Map<Object, Object> current = redis.opsForHash().entries(key);
                                long now = System.currentTimeMillis();
                                int attempts = 0;
                                if (!current.isEmpty()) {
                                    attempts = Integer.parseInt(value(current, "attempts"));
                                    if (attempts >= MAX_ATTEMPTS
                                            || now - Long.parseLong(value(current, "sentAt"))
                                                    < RESEND_DELAY_MS) {
                                        throw new AppException(RegistrationErrorCode.OTP_LIMIT);
                                    }
                                }
                                String otp =
                                        String.format(
                                                java.util.Locale.ROOT,
                                                "%06d",
                                                RANDOM.nextInt(1_000_000));
                                Long stored =
                                        redis.execute(
                                                STORE,
                                                List.of(key),
                                                encoder.encode(otp),
                                                Long.toString(now),
                                                Integer.toString(attempts),
                                                UUID.randomUUID().toString(),
                                                Long.toString(OTP_TTL_MS));
                                if (!Long.valueOf(1).equals(stored))
                                    throw new AppException(RegistrationErrorCode.UNAVAILABLE);
                                return new OtpDelivery(user.getEmail(), otp);
                            });
        } catch (DataAccessException
                | org.springframework.transaction.TransactionException
                | IllegalArgumentException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(delivery.email());
            message.setSubject("[AquaSuite] Verify your customer account");
            message.setText(
                    "Your account activation code is: "
                            + delivery.otp()
                            + "\nThis code expires in 5 minutes. If you did not register, ignore this email.");
            mail.send(message);
        } catch (RuntimeException ex) {
            throw new AppException(RegistrationErrorCode.MAIL_UNAVAILABLE);
        }
    }

    /** Activates once in PostgreSQL, then reuses login's JWT/session issuer after commit. */
    public LoginResponse verify(@Valid VerifyRegistrationRequest request) {
        UUID activated;
        try {
            activated =
                    transactions.execute(
                            tx -> {
                                User user = pendingUser(request.email());
                                String key = OTP_PREFIX + user.getId();
                                Map<Object, Object> state = redis.opsForHash().entries(key);
                                if (state.isEmpty())
                                    throw new AppException(RegistrationErrorCode.EXPIRED_OTP);
                                int attempts = Integer.parseInt(value(state, "attempts"));
                                if (attempts >= MAX_ATTEMPTS)
                                    throw new AppException(RegistrationErrorCode.OTP_LIMIT);
                                String version = value(state, "version");
                                if (!encoder.matches(request.otp(), value(state, "hash"))) {
                                    Long count = redis.execute(ATTEMPT, List.of(key), version);
                                    if (count == null || count < 0)
                                        throw new AppException(RegistrationErrorCode.EXPIRED_OTP);
                                    if (count >= MAX_ATTEMPTS)
                                        throw new AppException(RegistrationErrorCode.OTP_LIMIT);
                                    throw new AppException(
                                            RegistrationErrorCode.INVALID_OTP,
                                            "Invalid OTP code. Remaining attempts: "
                                                    + (MAX_ATTEMPTS - count));
                                }
                                // Recheck the role at activation rather than trusting its state at
                                // registration.
                                var customer =
                                        roles.findByRoleNameIgnoreCase("CUSTOMER")
                                                .filter(r -> r.isActive())
                                                .orElseThrow(
                                                        () ->
                                                                new AppException(
                                                                        RegistrationErrorCode
                                                                                .UNAVAILABLE));
                                if (user.getRoles().stream()
                                        .noneMatch(
                                                r -> r.getRoleId().equals(customer.getRoleId()))) {
                                    throw new AppException(RegistrationErrorCode.UNAVAILABLE);
                                }
                                if (!Long.valueOf(1)
                                        .equals(redis.execute(CONSUME, List.of(key), version))) {
                                    throw new AppException(RegistrationErrorCode.EXPIRED_OTP);
                                }
                                try {
                                    customerProfiles.createProfile(
                                            new CustomerRegistrationRequest(
                                                    user.getId(),
                                                    user.getFullName(),
                                                    user.getPhone(),
                                                    user.getEmail()));
                                } catch (RuntimeException ex) {
                                    throw new AppException(RegistrationErrorCode.UNAVAILABLE);
                                }
                                user.setStatus(UserStatus.ACTIVE);
                                users.saveAndFlush(user);
                                return user.getId();
                            });
        } catch (DataAccessException
                | org.springframework.transaction.TransactionException
                | IllegalArgumentException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        try {
            return auth.loginRegisteredUser(activated);
        } catch (RuntimeException ex) {
            // Activation is committed. The ordinary login API is the recovery path.
            throw new AppException(RegistrationErrorCode.LOGIN_REQUIRED);
        }
    }

    private User pendingUser(String email) {
        User user =
                users.findForRegistrationByEmail(email)
                        .orElseThrow(() -> new AppException(RegistrationErrorCode.NOT_PENDING));
        if (user.getStatus() != UserStatus.PENDING_ACTIVATION
                || !user.getUsername().equals("customer-" + user.getId())) {
            throw new AppException(RegistrationErrorCode.NOT_PENDING);
        }
        return user;
    }

    private static String value(Map<Object, Object> values, String name) {
        Object result = values.get(name);
        if (!(result instanceof String text))
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        return text;
    }

    private static String constraintName(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

    private record OtpDelivery(String email, String otp) {
        @Override
        public String toString() {
            return "OtpDelivery[redacted]";
        }
    }
}
