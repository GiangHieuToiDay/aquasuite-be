package com.aquasuite.ims.module.iam.internal.auth.service.impl;

import com.aquasuite.ims.module.iam.internal.auth.dto.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.auth.dto.response.LoginResponse;
import com.aquasuite.ims.module.iam.internal.auth.dto.request.RefreshRequest;
import com.aquasuite.ims.module.iam.internal.auth.dto.response.TokenResponse;
import com.aquasuite.ims.module.iam.internal.auth.service.AuthService;
import com.aquasuite.ims.module.iam.internal.auth.token.JwtTokenService;
import com.aquasuite.ims.module.iam.internal.auth.token.RedisSessionStore;
import com.aquasuite.ims.module.iam.internal.authorization.service.impl.CurrentAuthoritiesService;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final RedisSessionStore redisSessionStore;
    private final CurrentAuthoritiesService currentAuthoritiesService;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserRepository userRepository,
            JwtTokenService jwtTokenService,
            RedisSessionStore redisSessionStore,
            CurrentAuthoritiesService currentAuthoritiesService
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.redisSessionStore = redisSessionStore;
        this.currentAuthoritiesService = currentAuthoritiesService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        if (userRepository == null) {
            throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "User repository unavailable");
        }
        String email = request.getEmail() != null ? request.getEmail().trim() : "";

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (DisabledException e) {
            log.info("User with email '{}' is disabled/not active", email);
            throw new AppException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (DataAccessException e) {
            log.error("Database failure looking up user by email", e);
            throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }

        User user = userRepository.findWithRolesAndPermissionsByEmailIgnoreCase(email)
                .orElseThrow(() -> new AppException(AuthErrorCode.INVALID_CREDENTIALS));

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("User {} exists but status is {} (not ACTIVE)", user.getId(), user.getStatus());
            throw new AppException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        UUID sessionId = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant absoluteExpiresAt = now.plusSeconds(jwtTokenService.getSessionAbsoluteTtlSeconds());

        JwtTokenService.TokenPair tokenPair = jwtTokenService.issueTokenPair(
                user.getId(),
                sessionId,
                refreshJti,
                now,
                absoluteExpiresAt
        );

        // Commit Redis V2 session before returning
        redisSessionStore.createSession(sessionId, user.getId(), refreshJti, now, absoluteExpiresAt);

        List<String> cleanRoleNames = currentAuthoritiesService.resolveActiveRoleNames(user.getRoles());

        return LoginResponse.builder()
                .accessToken(tokenPair.accessToken())
                .refreshToken(tokenPair.refreshToken())
                .tokenType("Bearer")
                .expiresIn(tokenPair.expiresInSeconds())
                .user(LoginResponse.UserSummary.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .roles(cleanRoleNames)
                        .build())
                .build();
    }

    @Override
    public TokenResponse refresh(RefreshRequest request) {
        // 1. Verify cryptographic validity, issuer, audience, token_type=REFRESH, auth_version=2
        JwtTokenService.RefreshClaims claims = jwtTokenService.parseAndValidateRefreshToken(request.getRefreshToken());

        // 2. Load current user and verify ACTIVE status from PostgreSQL
        User user;
        try {
            user = currentAuthoritiesService.loadCurrentUser(claims.userId())
                    .orElseThrow(() -> new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        } catch (DataAccessException e) {
            log.error("Database failure looking up user by id for refresh", e);
            throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("User {} attempted refresh but status is {}", user.getId(), user.getStatus());
            throw new AppException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        // 3. Read active session from Redis to check absolute deadline
        RedisSessionStore.SessionData sessionData = redisSessionStore.getSessionData(claims.sessionId());
        if (sessionData == null) {
            // Session is absent in Redis. Let atomic rotateRefresh determine if valid tombstone exists (401 AUTH_007)
            // or if it is a replay (401 AUTH_006) or unknown token (401 AUTH_004)
            RedisSessionStore.RotationOutcome outcome = redisSessionStore.rotateRefresh(
                    claims.sessionId(),
                    user.getId(),
                    claims.jti(),
                    UUID.randomUUID(),
                    claims.expiresAt(),
                    Instant.now()
            );
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
        JwtTokenService.TokenPair successor = jwtTokenService.issueTokenPair(
                user.getId(),
                claims.sessionId(),
                newRefreshJti,
                now,
                absoluteExpiresAt
        );

        // 5. Execute atomic rotation in Redis
        RedisSessionStore.RotationOutcome outcome = redisSessionStore.rotateRefresh(
                claims.sessionId(),
                user.getId(),
                claims.jti(),
                newRefreshJti,
                claims.expiresAt(),
                now
        );

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
                log.warn("Confirmed replay detected for sid: {}, revoked whole session", claims.sessionId());
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
                log.error("Inconsistent or unknown Redis state during refresh rotation for sid: {}, outcome: {}", claims.sessionId(), outcome);
                throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
            }
            case UNKNOWN_TOKEN, INVALID -> {
                log.warn("Invalid refresh token or token mismatch during refresh rotation for sid: {}, outcome: {}", claims.sessionId(), outcome);
                throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
            default -> {
                log.warn("Unhandled outcome during refresh rotation for sid: {}, outcome: {}", claims.sessionId(), outcome);
                throw new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
            }
        }
    }

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
}
