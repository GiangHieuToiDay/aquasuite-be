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
import com.aquasuite.ims.security.AppUserDetails;
import com.aquasuite.ims.security.JwtTokenService;
import com.aquasuite.ims.service.crm.CustomerRegistrationService;
import com.aquasuite.ims.service.iam.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Triển khai {@link AuthService}: xử lý toàn bộ luồng xác thực của hệ thống.
 * <ul>
 *   <li>Đăng nhập, làm mới (xoay vòng refresh token), đăng xuất và xem thông tin người dùng hiện tại.</li>
 *   <li>Đăng ký tài khoản khách hàng bằng OTP gửi qua email.</li>
 *   <li>Đổi mật khẩu, quên mật khẩu và đặt lại mật khẩu bằng OTP.</li>
 * </ul>
 * Người dùng và quyền nằm ở PostgreSQL, phiên đăng nhập và OTP đăng ký nằm ở Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Chính sách mật khẩu mới: tối thiểu 8 ký tự, có chữ thường, chữ hoa, chữ số và ký tự đặc biệt. */
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

    /**
     * Xác thực email/mật khẩu qua AuthenticationManager, kiểm tra tài khoản ACTIVE
     * rồi chuyển sang bước phát hành token và lưu phiên.
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim() : "";

        // Xác thực email và mật khẩu qua AuthenticationManager (so khớp BCrypt trong DaoAuthenticationProvider)
        Authentication auth;
        try {
            auth =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (DisabledException e) {
            // Tài khoản không ở trạng thái ACTIVE
            log.info("User with email '{}' is disabled/not active", email);
            throw new AppException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            // Sai email hoặc mật khẩu: dùng chung một mã lỗi để không lộ email có tồn tại hay không
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (DataAccessException e) {
            log.error("Database failure looking up user by email", e);
            throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }

        // Dùng lại user do UserDetailsServiceImpl đã nạp, không truy vấn lần hai.
        User user = ((AppUserDetails) auth.getPrincipal()).getUser();

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
        // Mỗi lần đăng nhập tạo một phiên mới cùng jti cho refresh token đầu tiên
        UUID sessionId = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absoluteExpiresAt = now.plusSeconds(jwtTokenService.getSessionAbsoluteTtlSeconds());

        // Phát hành cặp access/refresh token gắn với phiên
        JwtTokenService.TokenPair tokenPair =
                jwtTokenService.issueTokenPair(
                        user.getId(), sessionId, refreshJti, now, absoluteExpiresAt);

        // Lưu phiên vào Redis (V2) trước khi trả token cho client
        redisSessionStore.createSession(
                sessionId, user.getId(), refreshJti, now, absoluteExpiresAt);

        // Tên vai trò (không có tiền tố ROLE_) để trả về cho client
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
        // 1. Xác minh chữ ký, issuer, audience, token_type=REFRESH và auth_version=2
        JwtTokenService.RefreshClaims claims =
                jwtTokenService.parseAndValidateRefreshToken(request.getRefreshToken());

        // 2. Tải người dùng hiện tại từ PostgreSQL và kiểm tra tài khoản còn ACTIVE
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

        // 3. Đọc phiên từ Redis để kiểm tra hạn tuyệt đối của phiên
        RedisSessionStore.SessionData sessionData =
                redisSessionStore.getSessionData(claims.sessionId());
        if (sessionData == null) {
            // Phiên không còn trong Redis: để rotateRefresh (nguyên tử) phân biệt phiên đã bị thu hồi
            // (401 AUTH_007), refresh token bị dùng lại (401 AUTH_006) hay token không xác định (401 AUTH_004)
            RedisSessionStore.RotationOutcome outcome =
                    redisSessionStore.rotateRefresh(
                            claims.sessionId(),
                            user.getId(),
                            claims.jti(),
                            UUID.randomUUID(),
                            claims.expiresAt(),
                            Instant.now());
            throw refreshRejection(outcome, claims.sessionId());
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absoluteExpiresAt = sessionData.absoluteExpiresAt();
        if (now.isAfter(absoluteExpiresAt) || now.equals(absoluteExpiresAt)) {
            throw new AppException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        // 4. Phát hành sẵn cặp token kế tiếp trong bộ nhớ
        UUID newRefreshJti = UUID.randomUUID();
        JwtTokenService.TokenPair successor =
                jwtTokenService.issueTokenPair(
                        user.getId(), claims.sessionId(), newRefreshJti, now, absoluteExpiresAt);

        // 5. Xoay vòng nguyên tử trong Redis: vô hiệu jti cũ và ghi nhận jti mới
        RedisSessionStore.RotationOutcome outcome =
                redisSessionStore.rotateRefresh(
                        claims.sessionId(),
                        user.getId(),
                        claims.jti(),
                        newRefreshJti,
                        claims.expiresAt(),
                        now);

        if (outcome != RedisSessionStore.RotationOutcome.SUCCESS) {
            throw refreshRejection(outcome, claims.sessionId());
        }
        return TokenResponse.builder()
                .accessToken(successor.accessToken())
                .refreshToken(successor.refreshToken())
                .tokenType("Bearer")
                .expiresIn(successor.expiresInSeconds())
                .build();
    }

    /** Chuyển kết quả xoay vòng thất bại trong Redis thành lỗi API tương ứng. */
    private AppException refreshRejection(
            RedisSessionStore.RotationOutcome outcome, UUID sessionId) {
        log.warn("Refresh rotation rejected for sid: {}, outcome: {}", sessionId, outcome);
        return switch (outcome) {
            case REVOKED -> new AppException(AuthErrorCode.SESSION_REVOKED);
            case REPLAY -> new AppException(AuthErrorCode.REFRESH_TOKEN_REUSED);
            case EXPIRED -> new AppException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
            case UNKNOWN_TOKEN, INVALID -> new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            case SUCCESS, INCONSISTENT, UNKNOWN ->
                    new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
        };
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

    // ── Đăng ký tài khoản bằng OTP ──────────────────────────────────────────

    /** Bước 1 đăng ký: kiểm tra trùng email/số điện thoại, lưu thông tin chờ kích hoạt kèm OTP vào Redis. */
    @Override
    public RegisterResponse register(RegisterRequest request) {
        PendingRegistration pending;
        try {
            // Từ chối nếu email hoặc số điện thoại đã có tài khoản
            checkDuplicates(request.email(), request.phone());
            // Mật khẩu được băm ngay, chỉ lưu bản băm vào Redis
            pending = new PendingRegistration(UUID.randomUUID(), request.fullName(),
                    request.email(), request.phone(), passwordEncoder.encode(request.password()));
            otpStore.save(pending);
        } catch (DataAccessException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        return new RegisterResponse(pending.id(), pending.email(), "PENDING_ACTIVATION");
    }

    /** Gửi lại OTP cho email đang chờ kích hoạt; email đã có tài khoản thì từ chối. */
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

    /**
     * Bước 2 đăng ký: xác minh OTP, tạo tài khoản trong một transaction rồi tự đăng nhập. Nếu tạo tài
     * khoản lỗi thì trả lại OTP (release) để người dùng thử lại.
     */
    @Override
    public LoginResponse verify(VerifyRegistrationRequest request) {
        // 1. Kiểm tra email chưa có tài khoản và giữ chỗ (claim) OTP hợp lệ
        RegistrationOtpStore.Claim claim;
        try {
            if (userRepository.existsByEmailIgnoreCase(request.email())) {
                throw new AppException(RegistrationErrorCode.NOT_PENDING);
            }
            claim = otpStore.verifyAndClaim(request.email(), request.otp());
        } catch (DataAccessException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        // 2. Tạo tài khoản trong transaction; lỗi thì nhả OTP để thử lại
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
            // Tài khoản đã được tạo: việc kiểm tra trùng trong cơ sở dữ liệu chặn dùng lại OTP cho tới khi khóa Redis hết hạn
        }
        // 3. Tạo phiên đăng nhập cho tài khoản vừa kích hoạt
        try {
            return this.loginRegisteredUser(userId);
        } catch (RuntimeException ex) {
            throw new AppException(RegistrationErrorCode.LOGIN_REQUIRED);
        }
    }

    /**
     * Tạo tài khoản khách hàng từ thông tin chờ kích hoạt: chèn người dùng, gán vai trò CUSTOMER, kích hoạt
     * và tạo hồ sơ khách hàng bên CRM. Chạy trong transaction của {@link #verify}.
     */
    private UUID createAccount(PendingRegistration pending) {
        // Kiểm tra trùng lần nữa trong transaction để tránh đăng ký song song
        checkDuplicates(pending.email(), pending.phone());
        // Vai trò mặc định của người tự đăng ký là CUSTOMER
        var role = roleRepository.findByRoleNameIgnoreCase("CUSTOMER")
                .filter(r -> r.isActive())
                .orElseThrow(() -> new AppException(RegistrationErrorCode.UNAVAILABLE));
        // Chèn người dùng bằng INSERT ... ON CONFLICT DO NOTHING để không ghi log câu lệnh chứa mật khẩu khi trùng
        int inserted = userRepository.insertPendingRegistration(pending.id(), "customer-" + pending.id(),
                pending.fullName(), pending.email(), pending.phone(), pending.passwordHash());
        if (inserted != 1) {
            checkDuplicates(pending.email(), pending.phone());
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        var user = userRepository.findById(pending.id())
                .orElseThrow(() -> new AppException(RegistrationErrorCode.UNAVAILABLE));
        // Gán vai trò, kích hoạt và đánh dấu đã xác minh
        user.getRoles().add(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setVerified(true);
        userRepository.saveAndFlush(user);
        // Tạo hồ sơ khách hàng bên module CRM
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

    /** Ném lỗi nếu email (không phân biệt hoa thường) hoặc số điện thoại đã thuộc về một tài khoản. */
    private void checkDuplicates(String email, String phone) {
        if (userRepository.existsByEmailIgnoreCase(email) || userRepository.existsByPhone(phone)) {
            throw new AppException(RegistrationErrorCode.DUPLICATE_ACCOUNT);
        }
    }

    /** Trả lại OTP đã giữ chỗ để người dùng nhập lại; lỗi Redis được bỏ qua. */
    private void release(RegistrationOtpStore.Claim claim) {
        try {
            otpStore.release(claim);
        } catch (RuntimeException ignored) {
            // Redis lỗi thì khóa giữ chỗ có thể còn tới khi hết hạn 5 phút
        }
    }

    // ── Mật khẩu ─────────────────────────────────────────────────────────────

    /** Đổi mật khẩu của người dùng đang đăng nhập: kiểm tra mật khẩu hiện tại, chính sách mật khẩu mới rồi lưu bản băm. */
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

        // Lưu mật khẩu mới dạng băm và ghi lại thời điểm đổi
        String newHash = passwordEncoder.encode(req.newPassword());
        user.setPasswordHash(newHash);
        user.setPasswordLastChangedAt(Instant.now());
        userRepository.save(user);

        // Thông báo cho các thành phần khác biết mật khẩu đã đổi
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new PasswordChangedEvent(user.getId(), "CHANGE_PASSWORD"));
        }

        return new PasswordActionResponse("Password changed successfully. Please log in again if required.");
    }

    /**
     * Tạo OTP khôi phục mật khẩu (6 chữ số, hiệu lực 10 phút, lưu dạng băm), giới hạn 5 lần gửi mỗi ngày
     * rồi gửi qua kênh email hoặc SMS.
     */
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

        // Giới hạn tối đa 5 lần gửi OTP mỗi ngày cho mỗi tài khoản
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

        // Sinh OTP 6 chữ số bằng SecureRandom; chỉ lưu bản băm, hiệu lực 10 phút
        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        user.setVerificationCode(passwordEncoder.encode(otp));
        user.setVerificationExpiredAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        userRepository.save(user);

        // Chọn chiến lược gửi OTP theo kênh (email hoặc SMS)
        OtpSenderStrategy strategy = otpStrategies.stream()
                .filter(s -> s.getSupportedChannel() != null
                        && s.getSupportedChannel().name().equalsIgnoreCase(req.channel().name()))
                .findFirst()
                .orElseThrow(() -> new AppException(IamPasswordErrorCode.SMS_CHANNEL_NOT_SUPPORTED_YET));

        strategy.sendRecoveryOtp(identifier, otp);

        return new PasswordActionResponse("A password reset code has been sent to your email.");
    }

    /** Đặt lại mật khẩu sau khi đối chiếu OTP khôi phục còn hạn; thành công thì xóa OTP và phát sự kiện đổi mật khẩu. */
    @Override
    @Transactional
    public PasswordActionResponse resetPasswordWithOtp(ResetPasswordWithOtpRequest req) {
        String identifier = req.identifier().trim();

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByPhone(identifier);
        }
        User user = userOpt.orElseThrow(() -> new AppException(IamPasswordErrorCode.USER_NOT_FOUND));

        // OTP phải tồn tại, chưa hết hạn và khớp với bản băm đã lưu
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

        // Đặt lại mật khẩu thành công thì kích hoạt lại tài khoản đang INACTIVE
        if (user.getStatus() == UserStatus.INACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new PasswordChangedEvent(user.getId(), "FORGOT_PASSWORD_RESET"));
        }

        return new PasswordActionResponse("Your password has been reset successfully. You may now sign in.");
    }

    /** Mật khẩu mới phải trùng phần xác nhận, không chứa khoảng trắng, tối đa 72 byte UTF-8 và đạt chính sách mật khẩu. */
    private void validatePasswordPolicyAndConfirmation(String newPassword, String confirmNewPassword) {
        if (newPassword == null
                || !newPassword.equals(confirmNewPassword)
                || newPassword.chars().anyMatch(Character::isWhitespace)
                || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !PASSWORD_POLICY_PATTERN.matcher(newPassword).matches()) {
            throw new AppException(IamPasswordErrorCode.WEAK_OR_MISMATCHED_PASSWORD);
        }
    }

    /** Mật khẩu mới không được trùng mật khẩu hiện tại. */
    private void validateNotSameAsCurrentPassword(User user, String rawNewPassword) {
        if (user.getPasswordHash() != null && passwordEncoder.matches(rawNewPassword, user.getPasswordHash())) {
            throw new AppException(IamPasswordErrorCode.SAME_AS_CURRENT_PASSWORD);
        }
    }

    /** Lấy ID người dùng hiện tại từ SecurityContext (JWT hoặc principal); không có thì trả null. */
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
