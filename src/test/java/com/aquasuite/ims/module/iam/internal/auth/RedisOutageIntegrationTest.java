package com.aquasuite.ims.module.iam.internal.auth;

import com.aquasuite.ims.module.iam.internal.auth.dto.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.auth.dto.request.RefreshRequest;
import com.aquasuite.ims.module.iam.internal.auth.security.AccessAuthenticationConverter;
import com.aquasuite.ims.module.iam.internal.auth.security.AuthSecurityErrorHandlers;
import com.aquasuite.ims.module.iam.internal.auth.security.JwtDecoderConfig;
import com.aquasuite.ims.module.iam.internal.auth.service.impl.AuthServiceImpl;
import com.aquasuite.ims.module.iam.internal.auth.token.JwtTokenService;
import com.aquasuite.ims.module.iam.internal.auth.token.RedisSessionStore;
import com.aquasuite.ims.module.iam.internal.authorization.service.impl.CurrentAuthoritiesService;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisOutageIntegrationTest {

    private static final String VALID_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtNjQtYnl0ZXMtbG9uZy1zby10aGlzLXNob3VsZC13b3JrIQ==";

    @Mock private AuthenticationManager authenticationManager;

    @Mock private UserRepository userRepository;

    @Mock private RedisSessionStore redisSessionStore;

    @Mock private CurrentAuthoritiesService currentAuthoritiesService;

    private JwtTokenService jwtTokenService;
    private AuthServiceImpl authService;
    private JwtDecoderConfig jwtDecoderConfig;
    private AuthSecurityErrorHandlers errorHandlers;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

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

        authService =
                new AuthServiceImpl(
                        authenticationManager,
                        userRepository,
                        jwtTokenService,
                        redisSessionStore,
                        currentAuthoritiesService);

        jwtDecoderConfig = new JwtDecoderConfig(jwtTokenService, redisSessionStore);
        ReflectionTestUtils.setField(jwtDecoderConfig, "issuer", "aquasuite-be");
        ReflectionTestUtils.setField(jwtDecoderConfig, "accessAudience", "aquasuite-api");
        jwtDecoderConfig.init();

        errorHandlers =
                new AuthSecurityErrorHandlers(
                        new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName(
            "AC-12: Redis outage trong lúc decode access token fail-closed với 503 AUTH_SERVICE_UNAVAILABLE và không signature-only bypass")
    void shouldFailClosedDuringResourceServerAccessWhenRedisDown() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absExp = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair =
                jwtTokenService.issueTokenPair(userId, sessionId, now, absExp);

        // Redis throws unavailable exception
        when(redisSessionStore.validateSession(any(), any()))
                .thenThrow(
                        new AppException(
                                AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                                "Redis connection timed out"));

        // Decoder must throw OAuth2AuthenticationException with service_unavailable
        assertThatThrownBy(() -> jwtDecoderConfig.decode(pair.accessToken()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(
                        e -> {
                            OAuth2AuthenticationException ex = (OAuth2AuthenticationException) e;
                            assertThat(ex.getError().getErrorCode())
                                    .isEqualTo("service_unavailable");
                        });

        // Error handler returns 503
        OAuth2AuthenticationException ex =
                new OAuth2AuthenticationException(
                        new org.springframework.security.oauth2.core.OAuth2Error(
                                "service_unavailable", "Redis down", null));
        errorHandlers.commence(request, response, ex);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString())
                .contains(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    @DisplayName("AC-12: Redis outage trong lúc Login fail-closed với 503 AUTH_SERVICE_UNAVAILABLE")
    void shouldFailClosedDuringLoginWhenRedisDown() {
        String email = "test@aquasuite.com";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of());

        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userRepository.findWithRolesAndPermissionsByEmailIgnoreCase(email))
                .thenReturn(Optional.of(user));

        // Redis createSession fails
        doThrow(new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Redis down"))
                .when(redisSessionStore)
                .createSession(
                        any(UUID.class),
                        any(UUID.class),
                        any(UUID.class),
                        any(Instant.class),
                        any(Instant.class));

        LoginRequest req = LoginRequest.builder().email(email).password("Password123!").build();

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName(
            "AC-12: Redis outage trong lúc Refresh fail-closed với 503 AUTH_SERVICE_UNAVAILABLE")
    void shouldFailClosedDuringRefreshWhenRedisDown() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absExp = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair =
                jwtTokenService.issueTokenPair(userId, sessionId, now, absExp);

        User user = new User();
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);

        when(currentAuthoritiesService.loadCurrentUser(userId)).thenReturn(Optional.of(user));
        when(redisSessionStore.getSessionData(sessionId))
                .thenThrow(new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Redis down"));

        RefreshRequest req = RefreshRequest.builder().refreshToken(pair.refreshToken()).build();

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName(
            "AC-12: Redis outage trong lúc Logout fail-closed với 503 AUTH_SERVICE_UNAVAILABLE")
    void shouldFailClosedDuringLogoutWhenRedisDown() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("sid")).thenReturn(sessionId.toString());
        when(jwt.getSubject()).thenReturn(userId.toString());

        doThrow(new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Redis down"))
                .when(redisSessionStore)
                .revokeSession(any(), any(), any());

        assertThatThrownBy(() -> authService.logout(jwt))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName(
            "BLOCKING-01: Redis rotateRefresh returning null result fails closed with 503 AUTH_SERVICE_UNAVAILABLE")
    void shouldFailClosedWhenRotateRefreshReturnsNullResult() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absExp = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair =
                jwtTokenService.issueTokenPair(userId, sessionId, now, absExp);

        User user = new User();
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);

        when(currentAuthoritiesService.loadCurrentUser(userId)).thenReturn(Optional.of(user));
        when(redisSessionStore.getSessionData(sessionId))
                .thenReturn(
                        new RedisSessionStore.SessionData(
                                userId, now, absExp, "ACTIVE", UUID.randomUUID()));
        when(redisSessionStore.rotateRefresh(any(), any(), any(), any(), any(), any()))
                .thenThrow(
                        new AppException(
                                AuthErrorCode.AUTH_SERVICE_UNAVAILABLE,
                                "Dịch vụ xác thực tạm thời không khả dụng"));

        RefreshRequest req = RefreshRequest.builder().refreshToken(pair.refreshToken()).build();

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName(
            "BLOCKING-01: Redis rotateRefresh returning UNKNOWN outcome fails closed with 503 AUTH_SERVICE_UNAVAILABLE")
    void shouldFailClosedWhenRotateRefreshReturnsUnknownOutcome() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absExp = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair =
                jwtTokenService.issueTokenPair(userId, sessionId, now, absExp);

        User user = new User();
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);

        when(currentAuthoritiesService.loadCurrentUser(userId)).thenReturn(Optional.of(user));
        when(redisSessionStore.getSessionData(sessionId))
                .thenReturn(
                        new RedisSessionStore.SessionData(
                                userId, now, absExp, "ACTIVE", UUID.randomUUID()));
        when(redisSessionStore.rotateRefresh(any(), any(), any(), any(), any(), any()))
                .thenReturn(RedisSessionStore.RotationOutcome.UNKNOWN);

        RefreshRequest req = RefreshRequest.builder().refreshToken(pair.refreshToken()).build();

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE));
    }
}
