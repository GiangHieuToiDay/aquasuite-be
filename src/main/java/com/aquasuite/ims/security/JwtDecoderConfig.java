package com.aquasuite.ims.security;

import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.BaseErrorCode;
import com.aquasuite.ims.exception.CommonErrorCode;
import com.aquasuite.ims.exception.iam.AuthErrorCode;
import com.aquasuite.ims.repository.iam.store.RedisSessionStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Giải mã access JWT theo hai bước: kiểm tra không trạng thái (chữ ký, thời hạn, các claim)
 * rồi kiểm tra có trạng thái (phiên còn hiệu lực trong Redis).
 */
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

    /** Dựng bộ giải mã Nimbus (HS512) kèm validator thời hạn và validator các claim sau khi nạp cấu hình. */
    @PostConstruct
    public void init() {
        this.nimbusJwtDecoder =
                NimbusJwtDecoder.withSecretKey(jwtTokenService.getSecretKey())
                        .macAlgorithm(MacAlgorithm.HS512)
                        .build();

        // Ép claim iss về chuỗi để so sánh với issuer cấu hình
        Map<String, Converter<Object, ?>> issuerAsString = Map.of(JwtClaimNames.ISS, Object::toString);
        this.nimbusJwtDecoder.setClaimSetConverter(
                MappedJwtClaimSetConverter.withDefaults(issuerAsString));

        // Cho phép sai lệch đồng hồ tối đa 30 giây giữa server và thời điểm phát hành token
        // để tránh từ chối nhầm access token hợp lệ do độ trễ mạng hoặc lệch clock.
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(30));
        this.nimbusJwtDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(timestampValidator, this::validateClaims));
    }

    /** Kiểm tra các claim bắt buộc của access token; trả về lỗi đầu tiên gặp phải. */
    private OAuth2TokenValidatorResult validateClaims(Jwt token) {
        String error = firstClaimError(token);
        return error == null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", error, null));
    }

    /** @return mô tả lỗi của claim đầu tiên không hợp lệ, hoặc null nếu tất cả hợp lệ */
    private String firstClaimError(Jwt token) {
        // Issuer phải đúng bên phát hành của hệ thống
        if (!issuer.equals(token.getClaimAsString(JwtClaimNames.ISS))) {
            return "Invalid issuer";
        }
        // Audience phải đúng một giá trị dành cho access token, tránh dùng nhầm refresh token
        List<String> aud = token.getAudience();
        if (aud == null || aud.size() != 1 || !accessAudience.equals(aud.get(0))) {
            return "Invalid audience";
        }
        // Loại token phải là ACCESS
        if (!JwtTokenService.TOKEN_TYPE_ACCESS.equals(
                token.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE))) {
            return "Invalid token_type";
        }
        // Token cũ không có auth_version=2 bị từ chối
        String versionError = authVersionError(token.getClaims().get(JwtTokenService.CLAIM_AUTH_VERSION));
        if (versionError != null) {
            return versionError;
        }
        // Bắt buộc có thời điểm phát hành và thời điểm hết hạn
        if (token.getIssuedAt() == null) {
            return "Missing iat claim";
        }
        if (token.getExpiresAt() == null) {
            return "Missing exp claim";
        }
        // sub (user), sid (phiên) và jti (token) đều phải là UUID hợp lệ
        String subError = uuidClaimError("sub", token.getSubject());
        if (subError != null) {
            return subError;
        }
        String sidError = uuidClaimError("sid", token.getClaimAsString(JwtTokenService.CLAIM_SID));
        if (sidError != null) {
            return sidError;
        }
        return uuidClaimError("jti", token.getId());
    }

    /** auth_version phải có và bằng 2; trả mô tả lỗi hoặc null nếu hợp lệ. */
    private String authVersionError(Object version) {
        if (version == null) {
            return "Missing auth_version claim";
        }
        try {
            return Long.parseLong(version.toString()) == JwtTokenService.AUTH_VERSION_V2
                    ? null
                    : "Unsupported auth_version";
        } catch (NumberFormatException e) {
            return "Invalid auth_version format";
        }
    }

    /** Claim bắt buộc phải có và đúng định dạng UUID; trả mô tả lỗi hoặc null nếu hợp lệ. */
    private String uuidClaimError(String name, String value) {
        if (value == null) {
            return "Missing " + name;
        }
        try {
            UUID.fromString(value);
            return null;
        } catch (IllegalArgumentException e) {
            return "Invalid " + name + " UUID";
        }
    }

    /**
     * Giải mã access token theo hai bước: kiểm tra chữ ký, thời hạn và claim; sau đó kiểm tra phiên
     * còn hiệu lực trong Redis. Lỗi được gắn mã lỗi vào request để trả response thống nhất.
     */
    @Override
    public Jwt decode(String token) throws JwtException {
        // 1. Kiểm tra không trạng thái: chữ ký, thời hạn, claim, auth_version=2, token_type=ACCESS
        Jwt jwt;
        try {
            jwt = nimbusJwtDecoder.decode(token);
        } catch (JwtException e) {
            log.warn("Stateless JWT decode/validation failed: {}", e.getMessage());
            setAuthError(CommonErrorCode.UNAUTHORIZED);
            throw e;
        }

        // 2. Kiểm tra có trạng thái: phiên Redis V2 còn ACTIVE
        requireActiveSession(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(JwtTokenService.CLAIM_SID)));
        return jwt;
    }

    /** Ném lỗi nếu phiên không còn ACTIVE trong Redis hoặc Redis không khả dụng. */
    private void requireActiveSession(UUID userId, UUID sessionId) {
        try {
            // Hỏi Redis trạng thái của phiên gắn với token
            switch (redisSessionStore.validateSession(sessionId, userId)) {
                case ACTIVE -> {
                    // Phiên còn hiệu lực: cho đi tiếp
                    return;
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
                    throw unavailable("Dịch vụ xác thực tạm thời không khả dụng");
                }
            }
        } catch (AppException e) {
            // Redis báo lỗi nghiệp vụ: trả 503 kèm mã lỗi tương ứng
            setAuthError(e.getErrorCode());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("service_unavailable", e.getMessage(), null));
        } catch (JwtException | OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            // Mọi lỗi Redis khác coi là dịch vụ xác thực không khả dụng
            log.error("Redis failure during session validation for sid: {}", sessionId, e);
            throw unavailable("Redis unavailable");
        }
    }

    /** Gắn mã lỗi dịch vụ xác thực không khả dụng vào request và tạo exception 503 để ném. */
    private OAuth2AuthenticationException unavailable(String description) {
        setAuthError(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
        return new OAuth2AuthenticationException(
                new OAuth2Error("service_unavailable", description, null));
    }

    /** Gắn mã lỗi vào request để {@link AuthSecurityErrorHandlers} trả đúng response. */
    private void setAuthError(BaseErrorCode errorCode) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            attrs.getRequest().setAttribute(AuthSecurityErrorHandlers.AUTH_ERROR_ATTRIBUTE, errorCode);
        }
    }
}
