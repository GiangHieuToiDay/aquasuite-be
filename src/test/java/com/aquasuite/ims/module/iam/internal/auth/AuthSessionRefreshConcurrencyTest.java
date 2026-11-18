package com.aquasuite.ims.module.iam.internal.auth;

import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.request.RefreshRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.TokenResponse;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.security.AuthSecurityConfig;
import com.aquasuite.ims.module.iam.internal.security.JwtDecoderConfig;
import com.aquasuite.ims.module.iam.internal.service.impl.AuthServiceImpl;
import com.aquasuite.ims.module.iam.internal.service.impl.CurrentAuthoritiesService;
import com.aquasuite.ims.module.iam.internal.token.JwtTokenService;
import com.aquasuite.ims.module.iam.internal.token.RedisSessionStore;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử duy trì phiên đăng nhập, làm mới token đồng thời, clock skew tolerance (30s),
 * bỏ qua stale Bearer header trên public auth endpoints và bảo toàn bất biến bảo mật UC-05.
 */
@ExtendWith(MockitoExtension.class)
class AuthSessionRefreshConcurrencyTest {

    private static final String VALID_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtNjQtYnl0ZXMtbG9uZy1zby10aGlzLXNob3VsZC13b3JrIQ==";

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private RedisSessionStore redisSessionStore;

    private JwtTokenService jwtTokenService;
    private JwtDecoderConfig jwtDecoderConfig;
    private AuthServiceImpl authService;
    private UUID userId;
    private User activeUser;

    @BeforeEach
    void setUp() {
        jwtTokenService =
                new JwtTokenService(
                        "aquasuite-be",
                        "aquasuite-api",
                        "aquasuite-auth",
                        1800,
                        1296000,
                        1296000,
                        VALID_SECRET);
        jwtTokenService.init();

        jwtDecoderConfig = new JwtDecoderConfig(jwtTokenService, redisSessionStore);
        ReflectionTestUtils.setField(jwtDecoderConfig, "issuer", "aquasuite-be");
        ReflectionTestUtils.setField(jwtDecoderConfig, "accessAudience", "aquasuite-api");
        jwtDecoderConfig.init();

        CurrentAuthoritiesService currentAuthoritiesService =
                new CurrentAuthoritiesService(userRepository);
        authService =
                new AuthServiceImpl(
                        authenticationManager,
                        userRepository,
                        jwtTokenService,
                        redisSessionStore,
                        currentAuthoritiesService);

        userId = UUID.randomUUID();
        Role ownerRole = new Role(UUID.randomUUID(), "SHOP_OWNER", "Chủ cửa hàng", true, null, Set.of());
        activeUser = new User();
        activeUser.setId(userId);
        activeUser.setUsername("owner_user");
        activeUser.setEmail("owner@aquasuite.com");
        activeUser.setStatus(UserStatus.ACTIVE);
        activeUser.setRoles(Set.of(ownerRole));

        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test
    @DisplayName(
            "Public Path BearerTokenResolver: Bỏ qua expired Authorization Bearer header trên /api/v1/auth/refresh và /api/v1/auth/login")
    void shouldIgnoreBearerHeaderOnPublicAuthPathsAndResolveOnProtectedPaths() {
        AuthSecurityConfig securityConfig = new AuthSecurityConfig(null, null, null, null);
        BearerTokenResolver resolver = securityConfig.publicPathAwareBearerTokenResolver();

        List<String> publicPaths =
                List.of(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/iam/register",
                        "/api/v1/iam/register/verifications",
                        "/api/v1/iam/register/otp-resends");

        for (String path : publicPaths) {
            MockHttpServletRequest publicReq = new MockHttpServletRequest("POST", path);
            publicReq.addHeader("Authorization", "Bearer expired-access-token-after-30-minutes");
            assertThat(resolver.resolve(publicReq))
                    .as("Public endpoint %s must ignore stale/expired Bearer header", path)
                    .isNull();
        }

        MockHttpServletRequest protectedReq = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        protectedReq.addHeader("Authorization", "Bearer valid-access-token-123");
        assertThat(resolver.resolve(protectedReq)).isEqualTo("valid-access-token-123");
    }

    @Test
    @DisplayName(
            "Clock Skew Tolerance (30s): Chấp nhận access token và refresh token vừa chạm mốc exp trong khoảng 30 giây, từ chối khi quá 30 giây")
    void shouldAllowTokensWithin30SecondsClockSkewAndRejectBeyondWindow() {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // 1. Token vừa quá hạn 10 giây (nằm trong ngưỡng dung sai 30 giây clock skew)
        Instant issuedSlightlyPast = now.minusSeconds(1810);
        Instant expired10sAgo = now.minusSeconds(10);
        JwtTokenService.TokenPair skewPair =
                jwtTokenService.issueTokenPair(
                        userId, sessionId, UUID.randomUUID(), issuedSlightlyPast, expired10sAgo);

        JwtTokenService.RefreshClaims refreshClaims =
                jwtTokenService.parseAndValidateRefreshToken(skewPair.refreshToken());
        assertThat(refreshClaims.userId()).isEqualTo(userId);
        assertThat(refreshClaims.sessionId()).isEqualTo(sessionId);

        JwtTokenService.AccessClaims accessClaims =
                jwtTokenService.parseAndValidateAccessToken(skewPair.accessToken());
        assertThat(accessClaims.userId()).isEqualTo(userId);

        when(redisSessionStore.validateSession(eq(sessionId), eq(userId)))
                .thenReturn(RedisSessionStore.ValidationState.ACTIVE);
        Jwt decodedJwt = jwtDecoderConfig.decode(skewPair.accessToken());
        assertThat(decodedJwt.getSubject()).isEqualTo(userId.toString());

        // 2. Token đã quá hạn 60 giây (vượt quá 30 giây dung sai) -> Bắt buộc từ chối
        Instant issuedLongPast = now.minusSeconds(1860);
        Instant expired60sAgo = now.minusSeconds(60);
        JwtTokenService.TokenPair trulyExpiredPair =
                jwtTokenService.issueTokenPair(
                        userId, sessionId, UUID.randomUUID(), issuedLongPast, expired60sAgo);

        assertThatThrownBy(
                        () ->
                                jwtTokenService.parseAndValidateRefreshToken(
                                        trulyExpiredPair.refreshToken()))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.EXPIRED_REFRESH_TOKEN));

        assertThatThrownBy(
                        () ->
                                jwtTokenService.parseAndValidateAccessToken(
                                        trulyExpiredPair.accessToken()))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));

        assertThatThrownBy(() -> jwtDecoderConfig.decode(trulyExpiredPair.accessToken()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName(
            "Concurrent Refresh & Sequential Session Continuity: Xoay vòng đồng thời chỉ 1 luồng thắng và luồng kế tiếp dùng token mới duy trì phiên bình thường")
    void shouldMaintainSessionAcrossConcurrentAndSequentialRefreshRotations() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID initialJti = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absoluteExpiresAt = now.plusSeconds(1296000);

        JwtTokenService.TokenPair initialPair =
                jwtTokenService.issueTokenPair(
                        userId, sessionId, initialJti, now, absoluteExpiresAt);

        CurrentAuthoritiesService concurrentAuthoritiesService =
                new CurrentAuthoritiesService(null) {
                    @Override
                    public Optional<User> loadCurrentUser(UUID id) {
                        return userId.equals(id) ? Optional.of(activeUser) : Optional.empty();
                    }
                };

        // Mô phỏng Redis Lua script đơn luồng nguyên tử: so sánh JTI theo giá trị (.equals)
        // vì UUID.fromString() khi giải mã JWT tạo instance UUID mới trên heap.
        RedisSessionStore concurrentSessionStore =
                new RedisSessionStore(null, null) {
                    private UUID activeJti = initialJti;

                    @Override
                    public synchronized SessionData getSessionData(UUID sid) {
                        if (!sessionId.equals(sid)) {
                            return null;
                        }
                        return new SessionData(
                                userId, now, absoluteExpiresAt, "ACTIVE", activeJti);
                    }

                    @Override
                    public synchronized RotationOutcome rotateRefresh(
                            UUID sid,
                            UUID uid,
                            UUID oldJti,
                            UUID newJti,
                            Instant oldExp,
                            Instant rotateNow) {
                        if (!sessionId.equals(sid) || !userId.equals(uid)) {
                            return RotationOutcome.UNKNOWN_TOKEN;
                        }
                        if (activeJti.equals(oldJti)) {
                            activeJti = newJti;
                            return RotationOutcome.SUCCESS;
                        }
                        return RotationOutcome.REPLAY;
                    }
                };

        AuthServiceImpl concurrentAuthService =
                new AuthServiceImpl(
                        authenticationManager,
                        null,
                        jwtTokenService,
                        concurrentSessionStore,
                        concurrentAuthoritiesService);

        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger replayRejectedCount = new AtomicInteger(0);
        AtomicReference<TokenResponse> winningResponse = new AtomicReference<>();

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(
                    executor.submit(
                            () -> {
                                ready.countDown();
                                start.await();
                                try {
                                    TokenResponse res =
                                            concurrentAuthService.refresh(
                                                    RefreshRequest.builder()
                                                            .refreshToken(
                                                                    initialPair.refreshToken())
                                                            .build());
                                    successCount.incrementAndGet();
                                    winningResponse.set(res);
                                } catch (AppException ex) {
                                    if (ex.getErrorCode() == AuthErrorCode.REFRESH_TOKEN_REUSED) {
                                        replayRejectedCount.incrementAndGet();
                                    } else {
                                        throw ex;
                                    }
                                }
                                return null;
                            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        for (Future<Void> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(replayRejectedCount.get()).isEqualTo(threads - 1);
        assertThat(winningResponse.get()).isNotNull();
        assertThat(winningResponse.get().getAccessToken()).isNotBlank();
        assertThat(winningResponse.get().getRefreshToken()).isNotBlank();

        // Tiếp tục xoay vòng lần 2 bằng refresh token mới do luồng thắng cấp -> Phiên duy trì ổn định
        TokenResponse secondRotation =
                concurrentAuthService.refresh(
                        RefreshRequest.builder()
                                .refreshToken(winningResponse.get().getRefreshToken())
                                .build());
        assertThat(secondRotation.getAccessToken()).isNotBlank();
        assertThat(secondRotation.getRefreshToken())
                .isNotBlank()
                .isNotEqualTo(winningResponse.get().getRefreshToken());
    }

    @Test
    @DisplayName(
            "UC-05 Security Invariant: Chặn làm mới phiên ngay lập tức với 403 AUTH_008 khi tài khoản bị LOCKED, INACTIVE hoặc PENDING_ACTIVATION")
    void shouldRejectRefreshWhenAccountIsLockedOrInactive() {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absoluteExpiresAt = now.plusSeconds(1296000);

        JwtTokenService.TokenPair validPair =
                jwtTokenService.issueTokenPair(userId, sessionId, now, absoluteExpiresAt);

        for (UserStatus nonActiveStatus :
                List.of(UserStatus.LOCKED, UserStatus.INACTIVE, UserStatus.PENDING_ACTIVATION)) {
            User disabledUser = new User();
            disabledUser.setId(userId);
            disabledUser.setUsername("locked_staff");
            disabledUser.setEmail("locked@aquasuite.com");
            disabledUser.setStatus(nonActiveStatus);
            disabledUser.setRoles(Set.of());

            when(userRepository.findWithRolesAndPermissionsById(userId))
                    .thenReturn(Optional.of(disabledUser));

            assertThatThrownBy(
                            () ->
                                    authService.refresh(
                                            RefreshRequest.builder()
                                                    .refreshToken(validPair.refreshToken())
                                                    .build()))
                    .isInstanceOf(AppException.class)
                    .satisfies(
                            e ->
                                    assertThat(((AppException) e).getErrorCode())
                                            .isEqualTo(AuthErrorCode.ACCOUNT_NOT_ACTIVE));
        }

        verify(redisSessionStore, never())
                .rotateRefresh(any(), any(), any(), any(), any(), any());
    }
}
