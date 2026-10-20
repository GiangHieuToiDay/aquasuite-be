package com.aquasuite.ims.module.iam.internal.auth.security;

import com.aquasuite.ims.module.iam.internal.auth.token.JwtTokenService;
import com.aquasuite.ims.module.iam.internal.auth.token.RedisSessionStore;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.BaseErrorCode;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtDecoderConfig implements JwtDecoder {

    private final JwtTokenService jwtTokenService;
    private final RedisSessionStore redisSessionStore;

    @Value("${aquasuite.security.jwt.issuer:aquasuite-be}")
    private String issuer;

    @Value("${aquasuite.security.jwt.access-audience:aquasuite-api}")
    private String accessAudience;

    private NimbusJwtDecoder nimbusJwtDecoder;

    @PostConstruct
    public void init() {
        this.nimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(jwtTokenService.getSecretKey())
                .macAlgorithm(MacAlgorithm.HS512)
                .build();

        Map<String, org.springframework.core.convert.converter.Converter<Object, ?>> customConverters = new HashMap<>();
        customConverters.put(org.springframework.security.oauth2.jwt.JwtClaimNames.ISS, Object::toString);
        this.nimbusJwtDecoder.setClaimSetConverter(org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter.withDefaults(customConverters));

        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator(Duration.ZERO);

        OAuth2TokenValidator<Jwt> claimsValidator = token -> {
            // Validate issuer
            String tokenIssuer = token.getClaimAsString("iss");
            if (!issuer.equals(tokenIssuer)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid issuer", null));
            }

            // Validate audience is exactly accessAudience
            List<String> aud = token.getAudience();
            if (aud == null || aud.size() != 1 || !accessAudience.equals(aud.get(0))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
            }

            // Validate token_type == 'ACCESS'
            String tokenType = token.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE);
            if (!JwtTokenService.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token_type", null));
            }

            // Validate auth_version == 2
            Object versionObj = token.getClaims().get(JwtTokenService.CLAIM_AUTH_VERSION);
            if (versionObj == null) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing auth_version claim", null));
            }
            try {
                long version = Long.parseLong(versionObj.toString());
                if (version != JwtTokenService.AUTH_VERSION_V2) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unsupported auth_version", null));
                }
            } catch (NumberFormatException e) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid auth_version format", null));
            }

            // Validate iat presence
            if (token.getIssuedAt() == null) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing iat claim", null));
            }

            // Validate exp presence
            if (token.getExpiresAt() == null) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing exp claim", null));
            }

            // Validate sub is valid UUID
            try {
                if (token.getSubject() == null) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing sub", null));
                }
                UUID.fromString(token.getSubject());
            } catch (Exception e) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid sub UUID", null));
            }

            // Validate sid is valid UUID
            try {
                String sid = token.getClaimAsString(JwtTokenService.CLAIM_SID);
                if (sid == null) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing sid", null));
                }
                UUID.fromString(sid);
            } catch (Exception e) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid sid UUID", null));
            }

            // Validate jti is valid UUID
            try {
                String jti = token.getId();
                if (jti == null) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing jti", null));
                }
                UUID.fromString(jti);
            } catch (Exception e) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid jti UUID", null));
            }

            return OAuth2TokenValidatorResult.success();
        };

        this.nimbusJwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator, claimsValidator));
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        // 1. Stateless validation (Nimbus JWT decode, signature, claims, auth_version=2, token_type=ACCESS)
        Jwt jwt;
        try {
            jwt = nimbusJwtDecoder.decode(token);
        } catch (JwtException e) {
            log.warn("Stateless JWT decode/validation failed: {}", e.getMessage());
            setAuthError(CommonErrorCode.UNAUTHORIZED);
            throw e;
        }

        // 2. Stateful validation (Redis V2 session check)
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID sessionId = UUID.fromString(jwt.getClaimAsString(JwtTokenService.CLAIM_SID));

        try {
            RedisSessionStore.ValidationState state = redisSessionStore.validateSession(sessionId, userId);
            switch (state) {
                case ACTIVE -> {
                    // Valid active session in Redis
                    return jwt;
                }
                case REVOKED -> {
                    log.info("Session sid: {} is revoked in Redis", sessionId);
                    setAuthError(AuthErrorCode.SESSION_REVOKED);
                    throw new BadJwtException("Session revoked");
                }
                case EXPIRED, NOT_FOUND -> {
                    log.info("Session sid: {} not found or expired in Redis", sessionId);
                    setAuthError(CommonErrorCode.UNAUTHORIZED);
                    throw new BadJwtException("Session invalid or expired");
                }
                case INCONSISTENT -> {
                    log.error("Session sid: {} in inconsistent state in Redis", sessionId);
                    setAuthError(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
                    throw new OAuth2AuthenticationException(new OAuth2Error("service_unavailable", "Dịch vụ xác thực tạm thời không khả dụng", null));
                }
            }
        } catch (AppException e) {
            setAuthError(e.getErrorCode());
            throw new OAuth2AuthenticationException(new OAuth2Error("service_unavailable", e.getMessage(), null));
        } catch (JwtException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis failure during session validation for sid: {}", sessionId, e);
            setAuthError(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
            throw new OAuth2AuthenticationException(new OAuth2Error("service_unavailable", "Redis unavailable", null));
        }

        return jwt;
    }

    private void setAuthError(BaseErrorCode errorCode) {
        RequestAttributes attr = RequestContextHolder.getRequestAttributes();
        if (attr instanceof ServletRequestAttributes servletRequestAttributes) {
            servletRequestAttributes.getRequest().setAttribute(AuthSecurityErrorHandlers.AUTH_ERROR_ATTRIBUTE, errorCode);
        }
    }
}
