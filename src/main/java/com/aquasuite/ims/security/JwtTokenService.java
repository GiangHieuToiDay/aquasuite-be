package com.aquasuite.ims.security;

import com.aquasuite.ims.exception.iam.AuthErrorCode;
import com.aquasuite.ims.exception.AppException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Phát hành và xác minh JWT của hệ thống: tạo cặp access/refresh token ký HS512, đọc lại refresh/access
 * token và kiểm tra chữ ký, issuer, audience, loại token, auth_version và các claim UUID. Khóa ký lấy
 * từ cấu hình (Base64, tối thiểu 64 byte); thiếu hoặc sai khóa thì ứng dụng không khởi động.
 */
@Slf4j
@Service
@Getter
public class JwtTokenService {

    /** Tên claim chứa loại token (ACCESS hoặc REFRESH). */
    public static final String CLAIM_TOKEN_TYPE = "token_type";
    /** Tên claim chứa ID phiên đăng nhập. */
    public static final String CLAIM_SID = "sid";
    /** Tên claim chứa phiên bản cơ chế xác thực. */
    public static final String CLAIM_AUTH_VERSION = "auth_version";

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    /** Phiên bản xác thực hiện hành; token cũ không có auth_version=2 bị từ chối. */
    public static final int AUTH_VERSION_V2 = 2;

    /** Sai lệch đồng hồ tối đa khi kiểm tra hạn token, tránh từ chối nhầm do lệch clock hoặc trễ mạng. */
    private static final long CLOCK_SKEW_SECONDS = 30;

    /** Bên phát hành token. */
    private final String issuer;
    /** Audience dành cho access token. */
    private final String accessAudience;
    /** Audience dành cho refresh token. */
    private final String refreshAudience;
    /** Thời sống danh nghĩa của access token (giây). */
    private final long accessTtlSeconds;
    /** Thời sống danh nghĩa của refresh token (giây). */
    private final long refreshTtlSeconds;
    /** Thời hạn tuyệt đối của một phiên đăng nhập (giây), không token nào sống quá mốc này. */
    private final long sessionAbsoluteTtlSeconds;
    /** Khóa ký dạng Base64 đọc từ cấu hình. */
    private final String rawSecret;

    private byte[] secretKeyBytes;
    private SecretKey secretKey;

    public JwtTokenService(
            @Value("${aquasuite.security.jwt.issuer:aquasuite-be}") String issuer,
            @Value("${aquasuite.security.jwt.access-audience:aquasuite-api}") String accessAudience,
            @Value("${aquasuite.security.jwt.refresh-audience:aquasuite-auth}")
                    String refreshAudience,
            @Value("${aquasuite.security.jwt.access-token-ttl-seconds:1800}") long accessTtlSeconds,
            @Value("${aquasuite.security.jwt.refresh-token-ttl-seconds:1296000}")
                    long refreshTtlSeconds,
            @Value("${aquasuite.security.jwt.session-absolute-ttl-seconds:1296000}")
                    long sessionAbsoluteTtlSeconds,
            @Value("${aquasuite.security.jwt.secret:}") String rawSecret) {
        this.issuer = issuer;
        this.accessAudience = accessAudience;
        this.refreshAudience = refreshAudience;
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.sessionAbsoluteTtlSeconds = sessionAbsoluteTtlSeconds;
        this.rawSecret = rawSecret;
    }

    /** Kiểm tra và nạp khóa ký khi khởi động; khóa thiếu, sai Base64 hoặc ngắn hơn 64 byte thì dừng ứng dụng. */
    @PostConstruct
    public void init() {
        // Khóa ký bắt buộc phải được cấu hình
        if (rawSecret == null || rawSecret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT signing secret is missing or empty. Startup aborted.");
        }

        // Giải mã Base64 và kiểm tra độ dài tối thiểu cho HS512
        byte[] keyBytes;
        try {
            byte[] decoded = Base64.getDecoder().decode(rawSecret.trim());
            if (decoded.length < 64) {
                throw new IllegalStateException(
                        "JWT signing secret must be at least 64 bytes after Base64 decode (actual: "
                                + decoded.length
                                + " bytes). Startup aborted.");
            }
            keyBytes = decoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "JWT signing secret is not valid Base64 or cannot be decoded. Startup aborted.",
                    e);
        }

        this.secretKeyBytes = keyBytes;
        this.secretKey = new SecretKeySpec(this.secretKeyBytes, "HmacSHA512");
        log.info(
                "JwtTokenService initialized successfully with HS512 secret length {} bytes",
                keyBytes.length);
    }

    /** Tính dấu vân tay SHA-256 (hex) của một chuỗi JWT để đối chiếu mà không lưu token gốc. */
    public String computeFingerprint(String jwtString) {
        if (jwtString == null) {
            return "";
        }
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(jwtString.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /** Các claim đã xác minh của refresh token. */
    public record RefreshClaims(
            UUID userId, UUID sessionId, UUID jti, Instant expiresAt, Instant issuedAt) {}

    /** Các claim đã xác minh của access token. */
    public record AccessClaims(
            UUID userId, UUID sessionId, UUID jti, Instant expiresAt, Instant issuedAt) {}

    /** Cặp token vừa phát hành cùng thời hạn và ID của từng token. */
    public record TokenPair(
            String accessToken,
            String refreshToken,
            UUID accessJti,
            UUID refreshJti,
            long expiresInSeconds,
            Instant accessExpiresAt,
            Instant refreshExpiresAt) {}

    /** Kết quả xác minh chữ ký và các claim dùng chung của một token. */
    private record VerifiedToken(JWTClaimsSet claims, UUID userId, UUID sessionId, UUID jti) {}

    /** Phát hành cặp token cho một phiên, tự sinh jti cho refresh token. */
    public TokenPair issueTokenPair(
            UUID userId, UUID sessionId, Instant now, Instant absoluteExpiresAt) {
        return issueTokenPair(userId, sessionId, UUID.randomUUID(), now, absoluteExpiresAt);
    }

    /**
     * Phát hành cặp access/refresh token với jti của refresh token do bên gọi chỉ định. Hạn mỗi token
     * không vượt quá hạn tuyệt đối của phiên.
     */
    public TokenPair issueTokenPair(
            UUID userId, UUID sessionId, UUID refreshJti, Instant now, Instant absoluteExpiresAt) {
        Instant normalizedNow = now.truncatedTo(ChronoUnit.SECONDS);
        Instant accessExpiresAt = cappedExpiry(normalizedNow, accessTtlSeconds, absoluteExpiresAt);
        Instant refreshExpiresAt = cappedExpiry(normalizedNow, refreshTtlSeconds, absoluteExpiresAt);
        long expiresIn = Math.max(0, Duration.between(normalizedNow, accessExpiresAt).toSeconds());

        UUID accessJti = UUID.randomUUID();
        String accessToken =
                generateSignedJwt(
                        userId, sessionId, accessJti, TOKEN_TYPE_ACCESS, accessAudience,
                        normalizedNow, accessExpiresAt);
        String refreshToken =
                generateSignedJwt(
                        userId, sessionId, refreshJti, TOKEN_TYPE_REFRESH, refreshAudience,
                        normalizedNow, refreshExpiresAt);

        return new TokenPair(
                accessToken, refreshToken, accessJti, refreshJti, expiresIn,
                accessExpiresAt, refreshExpiresAt);
    }

    /** Hạn token = min(now + ttl, hạn tuyệt đối của phiên), làm tròn đến giây. */
    private static Instant cappedExpiry(Instant now, long ttlSeconds, Instant absoluteExpiresAt) {
        Instant nominal = now.plusSeconds(ttlSeconds);
        return (nominal.isBefore(absoluteExpiresAt) ? nominal : absoluteExpiresAt)
                .truncatedTo(ChronoUnit.SECONDS);
    }

    /** Dựng claim và ký JWT bằng HS512; lỗi ký được quy về dịch vụ xác thực không khả dụng. */
    private String generateSignedJwt(
            UUID userId,
            UUID sessionId,
            UUID jti,
            String tokenType,
            String audience,
            Instant issuedAt,
            Instant expiresAt) {
        try {
            JWTClaimsSet claimsSet =
                    new JWTClaimsSet.Builder()
                            .issuer(issuer)
                            .audience(List.of(audience))
                            .subject(userId.toString())
                            .claim(CLAIM_SID, sessionId.toString())
                            .claim(CLAIM_TOKEN_TYPE, tokenType)
                            .claim(CLAIM_AUTH_VERSION, AUTH_VERSION_V2)
                            .jwtID(jti.toString())
                            .issueTime(Date.from(issuedAt))
                            .expirationTime(Date.from(expiresAt))
                            .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claimsSet);
            signedJWT.sign(new MACSigner(secretKeyBytes));
            return signedJWT.serialize();
        } catch (Exception e) {
            log.error("Failed to generate signed JWT for tokenType: {}", tokenType, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Không thể khởi tạo token");
        }
    }

    /** Xác minh refresh token và trả về claim; token sai ném INVALID_REFRESH_TOKEN, hết hạn ném EXPIRED_REFRESH_TOKEN. */
    public RefreshClaims parseAndValidateRefreshToken(String token) {
        VerifiedToken verified =
                verify(token, TOKEN_TYPE_REFRESH, refreshAudience, AuthErrorCode.INVALID_REFRESH_TOKEN);
        JWTClaimsSet claims = verified.claims();

        // Refresh token bắt buộc có thời điểm phát hành và thời điểm hết hạn
        Date issueTime = claims.getIssueTime();
        Date exp = claims.getExpirationTime();
        if (issueTime == null || exp == null) {
            log.warn("Missing iat or exp in refresh token");
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (isExpired(exp.toInstant())) {
            log.info("Refresh token expired at: {}", exp.toInstant());
            throw new AppException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }
        return new RefreshClaims(
                verified.userId(), verified.sessionId(), verified.jti(),
                exp.toInstant(), issueTime.toInstant());
    }

    /** Xác minh access token và trả về claim; mọi lỗi (kể cả hết hạn) ném INVALID_CREDENTIALS. */
    public AccessClaims parseAndValidateAccessToken(String token) {
        VerifiedToken verified =
                verify(token, TOKEN_TYPE_ACCESS, accessAudience, AuthErrorCode.INVALID_CREDENTIALS);
        JWTClaimsSet claims = verified.claims();

        // Access token bắt buộc có thời điểm hết hạn
        Date exp = claims.getExpirationTime();
        if (exp == null) {
            log.warn("Missing exp in access token");
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        if (isExpired(exp.toInstant())) {
            log.info("Access token expired at: {}", exp.toInstant());
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        Date issueTime = claims.getIssueTime();
        Instant issuedAt = issueTime != null ? issueTime.toInstant() : Instant.now();
        return new AccessClaims(
                verified.userId(), verified.sessionId(), verified.jti(), exp.toInstant(), issuedAt);
    }

    /** Hết hạn khi exp không sau (now - 30 giây), tức vẫn chấp nhận sai lệch đồng hồ. */
    private static boolean isExpired(Instant expiresAt) {
        return !expiresAt.isAfter(Instant.now().minusSeconds(CLOCK_SKEW_SECONDS));
    }

    /**
     * Phần xác minh dùng chung cho access và refresh token: định dạng, thuật toán HS512,
     * chữ ký, issuer, audience, token_type, auth_version=2 và các claim UUID (sub, sid, jti).
     * Mọi lỗi đều ném {@link AppException} với {@code errorCode} của loại token tương ứng.
     */
    private VerifiedToken verify(
            String token, String expectedType, String expectedAudience, AuthErrorCode errorCode) {
        if (token == null || token.trim().isEmpty()) {
            throw new AppException(errorCode);
        }

        // Phân tích JWT, bắt buộc thuật toán HS512 và kiểm tra chữ ký
        JWTClaimsSet claims;
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS512.equals(signedJWT.getHeader().getAlgorithm())
                    || !signedJWT.verify(new MACVerifier(secretKeyBytes))) {
                log.warn("{} token has wrong algorithm or invalid signature", expectedType);
                throw new AppException(errorCode);
            }
            claims = signedJWT.getJWTClaimsSet();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("{} token is malformed or cannot be verified", expectedType, e);
            throw new AppException(errorCode);
        }

        // Kiểm tra issuer, audience, loại token và phiên bản xác thực
        List<String> aud = claims.getAudience();
        if (!issuer.equals(claims.getIssuer())
                || aud == null
                || aud.size() != 1
                || !expectedAudience.equals(aud.get(0))
                || !expectedType.equals(claims.getClaim(CLAIM_TOKEN_TYPE))
                || !isAuthVersionV2(claims.getClaim(CLAIM_AUTH_VERSION))) {
            log.warn("{} token has invalid issuer, audience, token_type or auth_version", expectedType);
            throw new AppException(errorCode);
        }

        // Các claim sub, sid và jti đều phải là UUID hợp lệ
        try {
            return new VerifiedToken(
                    claims,
                    requiredUuid("sub", claims.getSubject()),
                    requiredUuid("sid", claims.getClaim(CLAIM_SID)),
                    requiredUuid("jti", claims.getJWTID()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID claim in {} token: {}", expectedType, e.getMessage());
            throw new AppException(errorCode);
        }
    }

    /** auth_version phải có và bằng 2; token cũ không có claim này bị từ chối. */
    private static boolean isAuthVersionV2(Object version) {
        if (version == null) {
            return false;
        }
        try {
            return Long.parseLong(version.toString()) == AUTH_VERSION_V2;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static UUID requiredUuid(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing " + name);
        }
        return UUID.fromString(value.toString());
    }
}
