package com.aquasuite.ims.module.iam.internal.security;

import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.RefreshRequest;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
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
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisabledUserIntegrationTest {

    private static final String VALID_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtNjQtYnl0ZXMtbG9uZy1zby10aGlzLXNob3VsZC13b3JrIQ==";

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisSessionStore redisSessionStore;

    @Mock
    private CurrentAuthoritiesService currentAuthoritiesService;

    private JwtTokenService jwtTokenService;
    private AuthServiceImpl authService;
    private AccessAuthenticationConverter accessAuthenticationConverter;

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

        authService = new AuthServiceImpl(
                authenticationManager,
                userRepository,
                jwtTokenService,
                redisSessionStore,
                currentAuthoritiesService
        );

        accessAuthenticationConverter = new AccessAuthenticationConverter(currentAuthoritiesService);

        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("AC-10: User bị disabled/non-active không thể đăng nhập -> ném ACCOUNT_NOT_ACTIVE (403 AUTH_008)")
    void shouldRejectLoginWhenUserIsDisabled() {
        String email = "disabled@aquasuite.com";
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("User account is disabled"));

        LoginRequest req = LoginRequest.builder().email(email).password("Password123!").build();

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(AuthErrorCode.ACCOUNT_NOT_ACTIVE));
    }

    @Test
    @DisplayName("AC-10: User bị disabled sau khi đăng nhập -> request bảo vệ kế tiếp bị từ chối 403 AUTH_008")
    void shouldDenyProtectedRequestImmediatelyWhenUserDisabledInDb() {
        UUID userId = UUID.randomUUID();
        User disabledUser = new User();
        disabledUser.setId(userId);
        disabledUser.setUsername("alice");
        disabledUser.setStatus(UserStatus.LOCKED); // Changed to LOCKED in DB

        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(userId.toString());

        when(currentAuthoritiesService.loadCurrentUser(userId)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> accessAuthenticationConverter.convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> {
                    OAuth2AuthenticationException ex = (OAuth2AuthenticationException) e;
                    assertThat(ex.getError().getErrorCode()).isEqualTo("account_not_active");
                });

        assertThat(request.getAttribute(AuthSecurityErrorHandlers.AUTH_ERROR_ATTRIBUTE))
                .isEqualTo(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("AC-10: User bị disabled khi gọi refresh token -> bị từ chối 403 AUTH_008")
    void shouldDenyRefreshWhenUserDisabledInDb() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absExp = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair = jwtTokenService.issueTokenPair(userId, sessionId, now, absExp);

        User disabledUser = new User();
        disabledUser.setId(userId);
        disabledUser.setStatus(UserStatus.INACTIVE);

        when(currentAuthoritiesService.loadCurrentUser(userId)).thenReturn(Optional.of(disabledUser));

        RefreshRequest req = RefreshRequest.builder().refreshToken(pair.refreshToken()).build();

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(AuthErrorCode.ACCOUNT_NOT_ACTIVE));
    }
}
