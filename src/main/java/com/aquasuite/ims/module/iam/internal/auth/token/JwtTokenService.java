package com.aquasuite.ims.module.iam.internal.auth.token;

import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.share.exception.AppException;
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
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Getter
public class JwtTokenService {

    public static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String CLAIM_SID = "sid";
    public static final String CLAIM_AUTH_VERSION = "auth_version";

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    public static final int AUTH_VERSION_V2 = 2;

    private final String issuer;
    private final String accessAudience;
    private final String refreshAudience;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final long sessionAbsoluteTtlSeconds;
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

    @PostConstruct
    public void init() {
        if (rawSecret == null || rawSecret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT signing secret is missing or empty. Startup aborted.");
        }

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

    public String computeFingerprint(String jwtString) {
        if (jwtString == null) {
            return "";
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash =
                    digest.digest(jwtString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public record RefreshClaims(
            UUID userId, UUID sessionId, UUID jti, Instant expiresAt, Instant issuedAt) {}

    public record AccessClaims(
            UUID userId, UUID sessionId, UUID jti, Instant expiresAt, Instant issuedAt) {}

    public record TokenPair(
            String accessToken,
            String refreshToken,
            UUID accessJti,
            UUID refreshJti,
            long expiresInSeconds,
            Instant accessExpiresAt,
            Instant refreshExpiresAt) {}

    public TokenPair issueTokenPair(
            UUID userId, UUID sessionId, Instant now, Instant absoluteExpiresAt) {
        return issueTokenPair(userId, sessionId, UUID.randomUUID(), now, absoluteExpiresAt);
    }

    public TokenPair issueTokenPair(
            UUID userId, UUID sessionId, UUID refreshJti, Instant now, Instant absoluteExpiresAt) {
        Instant normalizedNow = now.truncatedTo(ChronoUnit.SECONDS);

        // Calculate access exp = min(now + nominal, absoluteExpiresAt)
        Instant accessNominal = normalizedNow.plusSeconds(accessTtlSeconds);
        Instant accessExpiresAt =
                accessNominal.isBefore(absoluteExpiresAt) ? accessNominal : absoluteExpiresAt;
        accessExpiresAt = accessExpiresAt.truncatedTo(ChronoUnit.SECONDS);

        // Calculate refresh exp = min(now + nominal, absoluteExpiresAt)
        Instant refreshNominal = normalizedNow.plusSeconds(refreshTtlSeconds);
        Instant refreshExpiresAt =
                refreshNominal.isBefore(absoluteExpiresAt) ? refreshNominal : absoluteExpiresAt;
        refreshExpiresAt = refreshExpiresAt.truncatedTo(ChronoUnit.SECONDS);

        long expiresIn = Math.max(0, Duration.between(normalizedNow, accessExpiresAt).toSeconds());

        UUID accessJti = UUID.randomUUID();
        String accessToken =
                generateSignedJwt(
                        userId,
                        sessionId,
                        accessJti,
                        TOKEN_TYPE_ACCESS,
                        accessAudience,
                        normalizedNow,
                        accessExpiresAt);
        String refreshToken =
                generateSignedJwt(
                        userId,
                        sessionId,
                        refreshJti,
                        TOKEN_TYPE_REFRESH,
                        refreshAudience,
                        normalizedNow,
                        refreshExpiresAt);

        return new TokenPair(
                accessToken,
                refreshToken,
                accessJti,
                refreshJti,
                expiresIn,
                accessExpiresAt,
                refreshExpiresAt);
    }

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
            MACSigner signer = new MACSigner(secretKeyBytes);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            log.error("Failed to generate signed JWT for tokenType: {}", tokenType, e);
            throw new AppException(
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Không thể khởi tạo token");
        }
    }

    public RefreshClaims parseAndValidateRefreshToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(token);
        } catch (Exception e) {
            log.warn("Refresh token is not a valid JWT format");
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Verify Algorithm is strictly HS512
        if (!JWSAlgorithm.HS512.equals(signedJWT.getHeader().getAlgorithm())) {
            log.warn(
                    "Refresh token algorithm is not HS512: {}",
                    signedJWT.getHeader().getAlgorithm());
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Verify Signature
        try {
            MACVerifier verifier = new MACVerifier(secretKeyBytes);
            if (!signedJWT.verify(verifier)) {
                log.warn("Refresh token signature verification failed");
                throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
        } catch (Exception e) {
            log.warn("Error verifying refresh token signature", e);
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Verify Claims
        JWTClaimsSet claims;
        try {
            claims = signedJWT.getJWTClaimsSet();
        } catch (Exception e) {
            log.warn("Error reading refresh token claims", e);
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 1. Verify Issuer
        if (!issuer.equals(claims.getIssuer())) {
            log.warn("Invalid refresh token issuer: {}", claims.getIssuer());
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 2. Verify Audience is exactly refreshAudience
        List<String> aud = claims.getAudience();
        if (aud == null || aud.size() != 1 || !refreshAudience.equals(aud.get(0))) {
            log.warn("Invalid refresh token audience: {}", aud);
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 3. Verify token_type == 'REFRESH'
        Object tokenTypeObj = claims.getClaim(CLAIM_TOKEN_TYPE);
        if (!TOKEN_TYPE_REFRESH.equals(tokenTypeObj)) {
            log.warn("Invalid token_type claim in refresh token: {}", tokenTypeObj);
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 4. Verify auth_version == 2 (V2 requirement: legacy tokens without auth_version=2 are
        // rejected)
        Object versionObj = claims.getClaim(CLAIM_AUTH_VERSION);
        if (versionObj == null) {
            log.warn("Refresh token is missing auth_version claim (legacy token rejection)");
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        try {
            long version = Long.parseLong(versionObj.toString());
            if (version != AUTH_VERSION_V2) {
                log.warn("Refresh token has unsupported auth_version: {}", version);
                throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
        } catch (NumberFormatException e) {
            log.warn("Refresh token auth_version is not numeric: {}", versionObj);
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 5. Verify UUID claims
        UUID userId;
        UUID sessionId;
        UUID jti;
        try {
            String sub = claims.getSubject();
            if (sub == null) throw new IllegalArgumentException("Missing sub");
            userId = UUID.fromString(sub);

            Object sidObj = claims.getClaim(CLAIM_SID);
            if (sidObj == null) throw new IllegalArgumentException("Missing sid");
            sessionId = UUID.fromString(sidObj.toString());

            String jwtId = claims.getJWTID();
            if (jwtId == null) throw new IllegalArgumentException("Missing jti");
            jti = UUID.fromString(jwtId);
        } catch (Exception e) {
            log.warn("Invalid UUID claim in refresh token: {}", e.getMessage());
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Date issueTime = claims.getIssueTime();
        if (issueTime == null) {
            log.warn("Missing iat in refresh token");
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Date exp = claims.getExpirationTime();
        if (exp == null) {
            log.warn("Missing exp in refresh token");
            throw new AppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Instant expInstant = exp.toInstant();
        Instant now = Instant.now();
        // Check Expiration (strictly before or at now)
        if (expInstant.isBefore(now) || expInstant.equals(now)) {
            log.info("Refresh token expired at: {}, now: {}", expInstant, now);
            throw new AppException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        return new RefreshClaims(userId, sessionId, jti, expInstant, issueTime.toInstant());
    }

    public AccessClaims parseAndValidateAccessToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(token);
        } catch (Exception e) {
            log.warn("Access token is not a valid JWT format");
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (!JWSAlgorithm.HS512.equals(signedJWT.getHeader().getAlgorithm())) {
            log.warn(
                    "Access token algorithm is not HS512: {}",
                    signedJWT.getHeader().getAlgorithm());
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        try {
            MACVerifier verifier = new MACVerifier(secretKeyBytes);
            if (!signedJWT.verify(verifier)) {
                log.warn("Access token signature verification failed");
                throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
            }
        } catch (Exception e) {
            log.warn("Error verifying access token signature", e);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        JWTClaimsSet claims;
        try {
            claims = signedJWT.getJWTClaimsSet();
        } catch (Exception e) {
            log.warn("Error reading access token claims", e);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (!issuer.equals(claims.getIssuer())) {
            log.warn("Invalid access token issuer: {}", claims.getIssuer());
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        List<String> aud = claims.getAudience();
        if (aud == null || aud.size() != 1 || !accessAudience.equals(aud.get(0))) {
            log.warn("Invalid access token audience: {}", aud);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        Object tokenTypeObj = claims.getClaim(CLAIM_TOKEN_TYPE);
        if (!TOKEN_TYPE_ACCESS.equals(tokenTypeObj)) {
            log.warn("Invalid token_type claim in access token: {}", tokenTypeObj);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        Object versionObj = claims.getClaim(CLAIM_AUTH_VERSION);
        if (versionObj == null) {
            log.warn("Access token is missing auth_version claim (legacy token rejection)");
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        try {
            long version = Long.parseLong(versionObj.toString());
            if (version != AUTH_VERSION_V2) {
                log.warn("Access token has unsupported auth_version: {}", version);
                throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
            }
        } catch (NumberFormatException e) {
            log.warn("Access token auth_version is not numeric: {}", versionObj);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        UUID userId;
        UUID sessionId;
        UUID jti;
        try {
            String sub = claims.getSubject();
            if (sub == null) throw new IllegalArgumentException("Missing sub");
            userId = UUID.fromString(sub);

            Object sidObj = claims.getClaim(CLAIM_SID);
            if (sidObj == null) throw new IllegalArgumentException("Missing sid");
            sessionId = UUID.fromString(sidObj.toString());

            String jwtId = claims.getJWTID();
            if (jwtId == null) throw new IllegalArgumentException("Missing jti");
            jti = UUID.fromString(jwtId);
        } catch (Exception e) {
            log.warn("Invalid UUID claim in access token: {}", e.getMessage());
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        Date exp = claims.getExpirationTime();
        if (exp == null) {
            log.warn("Missing exp in access token");
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        Instant expInstant = exp.toInstant();
        Instant now = Instant.now();
        if (expInstant.isBefore(now) || expInstant.equals(now)) {
            log.info("Access token expired at: {}, now: {}", expInstant, now);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        Date issueTime = claims.getIssueTime();
        Instant issuedAt = issueTime != null ? issueTime.toInstant() : now;

        return new AccessClaims(userId, sessionId, jti, expInstant, issuedAt);
    }
}
