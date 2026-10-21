package com.aquasuite.ims.module.iam.internal.auth;

import com.aquasuite.ims.module.iam.internal.auth.security.AuthSecurityErrorHandlers;
import com.aquasuite.ims.module.iam.internal.auth.security.JwtDecoderConfig;
import com.aquasuite.ims.module.iam.internal.auth.token.JwtTokenService;
import com.aquasuite.ims.module.iam.internal.auth.token.RedisSessionStore;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtDecoderConfigTest {

    private static final String VALID_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtNjQtYnl0ZXMtbG9uZy1zby10aGlzLXNob3VsZC13b3JrIQ==";

    private JwtTokenService jwtTokenService;

    @Mock
    private RedisSessionStore redisSessionStore;

    private JwtDecoderConfig decoderConfig;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(
                "aquasuite-be",
                "aquasuite-api",
                "aquasuite-auth",
                1800,
                1296000,
                1296000,
                VALID_SECRET
        );
        jwtTokenService.init();

        decoderConfig = new JwtDecoderConfig(jwtTokenService, redisSessionStore);
        ReflectionTestUtils.setField(decoderConfig, "issuer", "aquasuite-be");
        ReflectionTestUtils.setField(decoderConfig, "accessAudience", "aquasuite-api");
        decoderConfig.init();

        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("V2 Access Token: Giải mã thành công khi chữ ký, claims và session Redis đều ACTIVE")
    void shouldDecodeValidAccessTokenWithActiveSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absExp = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair = jwtTokenService.issueTokenPair(userId, sessionId, now, absExp);

        when(redisSessionStore.validateSession(eq(sessionId), eq(userId)))
                .thenReturn(RedisSessionStore.ValidationState.ACTIVE);

        Jwt jwt = decoderConfig.decode(pair.accessToken());

        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("token_type")).isEqualTo("ACCESS");
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(sessionId.toString());
        assertThat(jwt.getClaimAsInstant("exp")).isNotNull();
    }

    @Test
    @DisplayName("Revoked Session: Từ chối access token khi Redis session ở trạng thái REVOKED (401 AUTH_007)")
    void shouldRejectAccessTokenWhenSessionIsRevoked() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absExp = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair = jwtTokenService.issueTokenPair(userId, sessionId, now, absExp);

        when(redisSessionStore.validateSession(eq(sessionId), eq(userId)))
                .thenReturn(RedisSessionStore.ValidationState.REVOKED);

        assertThatThrownBy(() -> decoderConfig.decode(pair.accessToken()))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("revoked");

        assertThat(request.getAttribute(AuthSecurityErrorHandlers.AUTH_ERROR_ATTRIBUTE))
                .isEqualTo(AuthErrorCode.SESSION_REVOKED);
    }

    @Test
    @DisplayName("Missing Session: Từ chối access token khi Redis session NOT_FOUND (401 AUTH_001)")
    void shouldRejectAccessTokenWhenSessionNotFound() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absExp = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair = jwtTokenService.issueTokenPair(userId, sessionId, now, absExp);

        when(redisSessionStore.validateSession(eq(sessionId), eq(userId)))
                .thenReturn(RedisSessionStore.ValidationState.NOT_FOUND);

        assertThatThrownBy(() -> decoderConfig.decode(pair.accessToken()))
                .isInstanceOf(BadJwtException.class);

        assertThat(request.getAttribute(AuthSecurityErrorHandlers.AUTH_ERROR_ATTRIBUTE))
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Fail-Closed: Redis outage trong lúc decode ném OAuth2AuthenticationException với service_unavailable (503 AUTH_009)")
    void shouldFailClosedWithServiceUnavailableWhenRedisIsDown() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absExp = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair = jwtTokenService.issueTokenPair(userId, sessionId, now, absExp);

        when(redisSessionStore.validateSession(eq(sessionId), eq(userId)))
                .thenThrow(new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Redis connection failed"));

        assertThatThrownBy(() -> decoderConfig.decode(pair.accessToken()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> {
                    OAuth2AuthenticationException ex = (OAuth2AuthenticationException) e;
                    assertThat(ex.getError().getErrorCode()).isEqualTo("service_unavailable");
                });

        assertThat(request.getAttribute(AuthSecurityErrorHandlers.AUTH_ERROR_ATTRIBUTE))
                .isEqualTo(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Token Type Separation: Không chấp nhận Refresh Token làm Access Token")
    void shouldRejectRefreshTokenOnAccessDecoder() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absExp = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair = jwtTokenService.issueTokenPair(userId, sessionId, now, absExp);

        // Pass refresh token to access decoder
        assertThatThrownBy(() -> decoderConfig.decode(pair.refreshToken()))
                .isInstanceOf(JwtException.class);
    }
}
