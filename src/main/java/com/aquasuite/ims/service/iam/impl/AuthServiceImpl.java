package com.aquasuite.ims.service.iam.impl;

import com.aquasuite.ims.dto.crm.request.CustomerRegistrationRequest;
import com.aquasuite.ims.dto.iam.request.ChangePasswordRequest;
import com.aquasuite.ims.dto.iam.request.ForgotPasswordRequest;
import com.aquasuite.ims.dto.iam.request.LoginRequest;
import com.aquasuite.ims.dto.iam.request.RefreshRequest;
import com.aquasuite.ims.dto.iam.request.RegisterRequest;
import com.aquasuite.ims.dto.iam.request.ResendRegistrationOtpRequest;
import com.aquasuite.ims.dto.iam.request.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.dto.iam.request.VerifyRegistrationRequest;
import com.aquasuite.ims.dto.iam.response.CurrentUserResponse;
import com.aquasuite.ims.dto.iam.response.LoginResponse;
import com.aquasuite.ims.dto.iam.response.PasswordActionResponse;
import com.aquasuite.ims.dto.iam.response.RegisterResponse;
import com.aquasuite.ims.dto.iam.response.TokenResponse;
import com.aquasuite.ims.entity.iam.User;
import com.aquasuite.ims.enums.iam.RecoveryChannel;
import com.aquasuite.ims.enums.iam.UserStatus;
import com.aquasuite.ims.event.iam.PasswordChangedEvent;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import com.aquasuite.ims.exception.iam.AuthErrorCode;
import com.aquasuite.ims.exception.iam.IamPasswordErrorCode;
import com.aquasuite.ims.exception.iam.RegistrationErrorCode;
import com.aquasuite.ims.integration.iam.OtpSenderStrategy;
import com.aquasuite.ims.repository.iam.RoleRepository;
import com.aquasuite.ims.repository.iam.UserRepository;
import com.aquasuite.ims.repository.iam.store.PendingRegistration;
import com.aquasuite.ims.repository.iam.store.RedisSessionStore;
import com.aquasuite.ims.repository.iam.store.RegistrationOtpStore;
import com.aquasuite.ims.security.JwtTokenService;
import com.aquasuite.ims.service.crm.CustomerRegistrationService;
import com.aquasuite.ims.service.iam.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
public class AuthServiceImpl implements AuthService {

    private static final Pattern PASSWORD_POLICY_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$"
    );

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final RedisSessionStore redisSessionStore;
    private final CurrentAuthoritiesService currentAuthoritiesService;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationOtpStore otpStore;
    private final TransactionTemplate transactions;
    private final CustomerRegistrationService customerProfiles;
    private final List<OtpSenderStrategy> otpStrategies;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public AuthServiceImpl(
            @Autowired(required = false) AuthenticationManager authenticationManager,
            @Autowired(required = false) UserRepository userRepository,
            @Autowired(required = false) JwtTokenService jwtTokenService,
            @Autowired(required = false) RedisSessionStore redisSessionStore,
            @Autowired(required = false) CurrentAuthoritiesService currentAuthoritiesService,
            @Autowired(required = false) RoleRepository roleRepository,
            @Autowired(required = false) PasswordEncoder passwordEncoder,
            @Autowired(required = false) RegistrationOtpStore otpStore,
            @Autowired(required = false) TransactionTemplate transactions,
            @Autowired(required = false) CustomerRegistrationService customerProfiles,
            @Autowired(required = false) List<OtpSenderStrategy> otpStrategies,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.redisSessionStore = redisSessionStore;
        this.currentAuthoritiesService = currentAuthoritiesService;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpStore = otpStore;
        this.transactions = transactions;
        this.customerProfiles = customerProfiles;
        this.otpStrategies = otpStrategies != null ? otpStrategies : List.of();
        this.eventPublisher = eventPublisher;
    }

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtTokenService jwtTokenService,
            RedisSessionStore redisSessionStore,
            CurrentAuthoritiesService currentAuthoritiesService) {
        this(authenticationManager, userRepository, jwtTokenService, redisSessionStore,
                currentAuthoritiesService, null, null, null, null, null, null, null);
    }

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            List<OtpSenderStrategy> otpStrategies,
            ApplicationEventPublisher eventPublisher) {
        this(null, userRepository, null, null, null, null, passwordEncoder, null, null, null, otpStrategies, eventPublisher);
    }

    /**
     * Xác thực email/mật khẩu qua AuthenticationManager, kiểm tra tài khoản ACTIVE
     * rồi chuyển sang bước phát hành token và lưu phiên.
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        if (userRepository == null) {
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "User repository unavailable");
        }
        String email = request.getEmail() != null ? request.getEmail().trim() : "";

        try {
            Authentication auth =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (DisabledException e) {
            log.info("User with email '{}' is disabled/not active", email);
            throw new AppException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (DataAccessException e) {
            log.error("Database failure looking up user by email", e);
            throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }

        User user =
                userRepository
                        .findWithRolesAndPermissionsByEmailIgnoreCase(email)
                        .orElseThrow(() -> new AppException(AuthErrorCode.INVALID_CREDENTIALS));

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info(
                    "User {} exists but status is {} (not ACTIVE)", user.getId(), user.getStatus());
            throw new AppException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        return createSession(user);
    }

    /**
     * Tải tài khoản vừa kích hoạt theo ID, bảo đảm tài khoản ACTIVE và tạo phiên
     * mà không yêu cầu người dùng nhập lại mật khẩu.
     */
    @Override
    public LoginResponse loginRegisteredUser(UUID userId) {
        User user =
                currentAuthoritiesService
                        .loadCurrentUser(userId)
                        .orElseThrow(() -> new AppException(AuthErrorCode.INVALID_CREDENTIALS));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        return createSession(user);
    }

    /**
     * Tạo session ID và refresh-token ID, phát hành cặp JWT, lưu phiên vào Redis
     * rồi dựng response người dùng với danh sách vai trò đang hoạt động.
     */
    private LoginResponse createSession(User user) {
        UUID sessionId = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absoluteExpiresAt = now.plusSeconds(jwtTokenService.getSessionAbsoluteTtlSeconds());

        JwtTokenService.TokenPair tokenPair =
                jwtTokenService.issueTokenPair(
                        user.getId(), sessionId, refreshJti, now, absoluteExpiresAt);

        // Commit Redis V2 session before returning
        redisSessionStore.createSession(
                sessionId, user.getId(), refreshJti, now, absoluteExpiresAt);

        List<String> cleanRoleNames =
                currentAuthoritiesService.resolveActiveRoleNames(user.getRoles());

        return LoginResponse.builder()
                .accessToken(tokenPair.accessToken())
                .refreshToken(tokenPair.refreshToken())
                .tokenType("Bearer")
                .expiresIn(tokenPair.expiresInSeconds())
                .user(
                        LoginResponse.UserSummary.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .email(user.getEmail())
                                .roles(cleanRoleNames)
                                .build())
                .build();
    }

    /**
     * Xác minh chữ ký và claim của refresh token, kiểm tra tài khoản/database,
     * sau đó xoay vòng token nguyên tử trong Redis để phát hiện replay.
     */
    @Override
    public TokenResponse refresh(RefreshRequest request) {
        // 1. Verify cryptographic validity, issuer, audience, token_type=REFRESH, auth_version=2
        JwtTokenService.RefreshClaims claims =
                jwtTokenService.parseAndValidateRefreshToken(request.getRefreshToken());

        // 2. Load current user and verify ACTIVE status from PostgreSQL
        User user;
        try {
            user =
                    currentAuthoritiesService
                            .loadCurrentUser(claims.userId())
                            .orElseThrow(
                                    () -> new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        } catch (DataAccessException e) {
            log.error("Database failure looking up user by id for refresh", e);
            throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("User {} attempted refresh but status is {}", user.getId(), user.getStatus());
            throw new AppException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        // 3. Read active session from Redis to check absolute deadline
        RedisSessionStore.SessionData sessionData =
                redisSessionStore.getSessionData(claims.sessionId());
        if (sessionData == null) {
            // Session is absent in Redis. Let atomic rotateRefresh determine if valid tombstone
            // exists (401 AUTH_007)
            // or if it is a replay (401 AUTH_006) or unknown token (401 AUTH_004)
            RedisSessionStore.RotationOutcome outcome =
                    redisSessionStore.rotateRefresh(
                            claims.sessionId(),
                            user.getId(),
                            claims.jti(),
                            UUID.randomUUID(),
                            claims.expiresAt(),
                            Instant.now());
            if (outcome == RedisSessionStore.RotationOutcome.REVOKED) {
                throw new AppException(AuthErrorCode.SESSION_REVOKED);
            } else if (outcome == RedisSessionStore.RotationOutcome.REPLAY) {
                throw new AppException(AuthErrorCode.REFRESH_TOKEN_REUSED);
            } else if (outcome == RedisSessionStore.RotationOutcome.EXPIRED) {
                throw new AppException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
            } else if (outcome == RedisSessionStore.RotationOutcome.INCONSISTENT
                    || outcome == RedisSessionStore.RotationOutcome.UNKNOWN) {
                throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
            } else if (outcome == RedisSessionStore.RotationOutcome.UNKNOWN_TOKEN
                    || outcome == RedisSessionStore.RotationOutcome.INVALID) {
                throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
            throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absoluteExpiresAt = sessionData.absoluteExpiresAt();
        if (now.isAfter(absoluteExpiresAt) || now.equals(absoluteExpiresAt)) {
            throw new AppException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        // 4. Generate successor token pair in memory
        UUID newRefreshJti = UUID.randomUUID();
        JwtTokenService.TokenPair successor =
                jwtTokenService.issueTokenPair(
                        user.getId(), claims.sessionId(), newRefreshJti, now, absoluteExpiresAt);

        // 5. Execute atomic rotation in Redis
        RedisSessionStore.RotationOutcome outcome =
                redisSessionStore.rotateRefresh(
                        claims.sessionId(),
                        user.getId(),
                        claims.jti(),
                        newRefreshJti,
                        claims.expiresAt(),
                        now);

        switch (outcome) {
            case SUCCESS -> {
                return TokenResponse.builder()
                        .accessToken(successor.accessToken())
                        .refreshToken(successor.refreshToken())
                        .tokenType("Bearer")
                        .expiresIn(successor.expiresInSeconds())
                        .build();
            }
            case REPLAY -> {
                log.warn(
                        "Confirmed replay detected for sid: {}, revoked whole session",
                        claims.sessionId());
                throw new AppException(AuthErrorCode.REFRESH_TOKEN_REUSED);
            }
            case REVOKED -> {
                log.info("Session sid: {} is revoked", claims.sessionId());
                throw new AppException(AuthErrorCode.SESSION_REVOKED);
            }
            case EXPIRED -> {
                log.info("Session sid: {} is expired", claims.sessionId());
                throw new AppException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
            }
            case INCONSISTENT, UNKNOWN -> {
                log.error(
                        "Inconsistent or unknown Redis state during refresh rotation for sid: {}, outcome: {}",
                        claims.sessionId(),
                        outcome);
                throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
            }
            case UNKNOWN_TOKEN, INVALID -> {
                log.warn(
                        "Invalid refresh token or token mismatch during refresh rotation for sid: {}, outcome: {}",
                        claims.sessionId(),
                        outcome);
                throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
            default -> {
                log.warn(
                        "Unhandled outcome during refresh rotation for sid: {}, outcome: {}",
                        claims.sessionId(),
                        outcome);
                throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
            }
        }
    }

    /**
     * Đọc user ID và session ID từ access JWT rồi đánh dấu phiên tương ứng đã
     * bị thu hồi trong Redis với lý do đăng xuất.
     */
    @Override
    public void logout(Jwt jwt) {
        if (jwt == null) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        }

        String sidStr = jwt.getClaimAsString(JwtTokenService.CLAIM_SID);
        String subStr = jwt.getSubject();
        if (sidStr == null || subStr == null) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        }

        UUID sessionId;
        UUID userId;
        try {
            sessionId = UUID.fromString(sidStr);
            userId = UUID.fromString(subStr);
        } catch (IllegalArgumentException e) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        }

        redisSessionStore.revokeSession(sessionId, userId, "LOGOUT");
    }

    /**
     * Lấy user ID từ access JWT đã xác thực, đọc lại hồ sơ và vai trò từ PostgreSQL
     * để thay đổi quyền có hiệu lực ngay ở request kế tiếp; method này không ghi Redis.
     */
    @Override
    public CurrentUserResponse getCurrentUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        }

        User user;
        try {
            user =
                    currentAuthoritiesService
                            .loadCurrentUser(userId)
                            .orElseThrow(() -> new AppException(CommonErrorCode.UNAUTHORIZED));
        } catch (DataAccessException | TransactionException e) {
            log.error("Database failure loading current user profile for userId: {}", userId, e);
            throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("User {} requested current profile but status is {}", userId, user.getStatus());
            throw new AppException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        List<String> roleNames = currentAuthoritiesService.resolveActiveRoleNames(user.getRoles());

        return CurrentUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(roleNames != null ? List.copyOf(roleNames) : List.of())
                .build();
    }

    // ── Registration Logic (from RegisterService) ──────────────────────────

    @Override
    public RegisterResponse register(RegisterRequest request) {
        PendingRegistration pending;
        try {
            checkDuplicates(request.email(), request.phone());
            pending = new PendingRegistration(UUID.randomUUID(), request.fullName(),
                    request.email(), request.phone(), passwordEncoder.encode(request.password()));
            otpStore.save(pending);
        } catch (DataAccessException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        return new RegisterResponse(pending.id(), pending.email(), "PENDING_ACTIVATION");
    }

    @Override
    public void resend(ResendRegistrationOtpRequest request) {
        try {
            if (userRepository.existsByEmailIgnoreCase(request.email())) {
                throw new AppException(RegistrationErrorCode.NOT_PENDING);
            }
            otpStore.rotateOtp(request.email());
        } catch (DataAccessException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
    }

    @Override
    public LoginResponse verify(VerifyRegistrationRequest request) {
        RegistrationOtpStore.Claim claim;
        try {
            if (userRepository.existsByEmailIgnoreCase(request.email())) {
                throw new AppException(RegistrationErrorCode.NOT_PENDING);
            }
            claim = otpStore.verifyAndClaim(request.email(), request.otp());
        } catch (DataAccessException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        UUID userId;
        try {
            userId = transactions.execute(tx -> createAccount(claim.registration()));
        } catch (RuntimeException ex) {
            release(claim);
            if (ex instanceof AppException app) throw app;
            if (ex instanceof DataIntegrityViolationException) {
                throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
            }
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        try {
            otpStore.complete(claim);
        } catch (RuntimeException ignored) {
            // Account is committed; database duplicate checks prevent replay until Redis expires.
        }
        try {
            return this.loginRegisteredUser(userId);
        } catch (RuntimeException ex) {
            throw new AppException(RegistrationErrorCode.LOGIN_REQUIRED);
        }
    }

    private UUID createAccount(PendingRegistration pending) {
        checkDuplicates(pending.email(), pending.phone());
        var role = roleRepository.findByRoleNameIgnoreCase("CUSTOMER")
                .filter(r -> r.isActive())
                .orElseThrow(() -> new AppException(RegistrationErrorCode.UNAVAILABLE));
        int inserted = userRepository.insertPendingRegistration(pending.id(), "customer-" + pending.id(),
                pending.fullName(), pending.email(), pending.phone(), pending.passwordHash());
        if (inserted != 1) {
            checkDuplicates(pending.email(), pending.phone());
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        var user = userRepository.findById(pending.id())
                .orElseThrow(() -> new AppException(RegistrationErrorCode.UNAVAILABLE));
        user.getRoles().add(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setVerified(true);
        userRepository.saveAndFlush(user);
        try {
            customerProfiles.createProfile(new CustomerRegistrationRequest(user.getId(),
                    user.getFullName(), user.getPhone(), user.getEmail()));
        } catch (AppException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        return user.getId();
    }

    private void checkDuplicates(String email, String phone) {
        if (userRepository.existsByEmailIgnoreCase(email) || userRepository.existsByPhone(phone)) {
            throw new AppException(RegistrationErrorCode.DUPLICATE_ACCOUNT);
        }
    }

    private void release(RegistrationOtpStore.Claim claim) {
        try {
            otpStore.release(claim);
        } catch (RuntimeException ignored) {
            // Failed dependency may keep the claim until the existing five-minute TTL expires.
        }
    }

    // ── Password Logic (from PasswordService) ──────────────────────────────

    @Override
    @Transactional
    public PasswordActionResponse changePassword(ChangePasswordRequest req) {
        UUID userId = resolveCurrentUserId();
        if (userId == null) {
            throw new AppException(IamPasswordErrorCode.INCORRECT_CURRENT_PASSWORD);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(IamPasswordErrorCode.USER_NOT_FOUND));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new AppException(IamPasswordErrorCode.INCORRECT_CURRENT_PASSWORD);
        }

        validatePasswordPolicyAndConfirmation(req.newPassword(), req.confirmNewPassword());
        validateNotSameAsCurrentPassword(user, req.newPassword());

        String newHash = passwordEncoder.encode(req.newPassword());
        user.setPasswordHash(newHash);
        user.setPasswordLastChangedAt(Instant.now());
        userRepository.save(user);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new PasswordChangedEvent(user.getId(), "CHANGE_PASSWORD"));
        }

        return new PasswordActionResponse("Password changed successfully. Please log in again if required.");
    }

    @Override
    @Transactional
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

        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        user.setVerificationCode(passwordEncoder.encode(otp));
        user.setVerificationExpiredAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        userRepository.save(user);

        OtpSenderStrategy strategy = otpStrategies.stream()
                .filter(s -> s.getSupportedChannel() != null
                        && s.getSupportedChannel().name().equalsIgnoreCase(req.channel().name()))
                .findFirst()
                .orElseThrow(() -> new AppException(IamPasswordErrorCode.SMS_CHANNEL_NOT_SUPPORTED_YET));

        strategy.sendRecoveryOtp(identifier, otp);

        return new PasswordActionResponse("A password reset code has been sent to your email.");
    }

    @Override
    @Transactional
    public PasswordActionResponse resetPasswordWithOtp(ResetPasswordWithOtpRequest req) {
        String identifier = req.identifier().trim();

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByPhone(identifier);
        }
        User user = userOpt.orElseThrow(() -> new AppException(IamPasswordErrorCode.USER_NOT_FOUND));

        if (user.getVerificationCode() == null || user.getVerificationExpiredAt() == null
                || Instant.now().isAfter(user.getVerificationExpiredAt())) {
            throw new AppException(IamPasswordErrorCode.INVALID_OR_EXPIRED_OTP);
        }

        if (!passwordEncoder.matches(req.otpCode().trim(), user.getVerificationCode())) {
            throw new AppException(IamPasswordErrorCode.INVALID_OR_EXPIRED_OTP);
        }

        validatePasswordPolicyAndConfirmation(req.newPassword(), req.confirmNewPassword());
        validateNotSameAsCurrentPassword(user, req.newPassword());

        String newHash = passwordEncoder.encode(req.newPassword());
        user.setPasswordHash(newHash);
        user.setPasswordLastChangedAt(Instant.now());
        user.setVerificationCode(null);
        user.setVerificationExpiredAt(null);

        if (user.getStatus() == UserStatus.INACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new PasswordChangedEvent(user.getId(), "FORGOT_PASSWORD_RESET"));
        }

        return new PasswordActionResponse("Your password has been reset successfully. You may now sign in.");
    }

    private void validatePasswordPolicyAndConfirmation(String newPassword, String confirmNewPassword) {
        if (newPassword == null || !newPassword.equals(confirmNewPassword)
                || !PASSWORD_POLICY_PATTERN.matcher(newPassword).matches()) {
            throw new AppException(IamPasswordErrorCode.WEAK_OR_MISMATCHED_PASSWORD);
        }
    }

    private void validateNotSameAsCurrentPassword(User user, String rawNewPassword) {
        if (user.getPasswordHash() != null && passwordEncoder.matches(rawNewPassword, user.getPasswordHash())) {
            throw new AppException(IamPasswordErrorCode.SAME_AS_CURRENT_PASSWORD);
        }
    }

    private UUID resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                return UUID.fromString(jwt.getToken().getSubject());
            } catch (IllegalArgumentException ignored) {}
        }
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            try {
                return UUID.fromString(auth.getName());
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }
}
