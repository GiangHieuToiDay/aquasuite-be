package com.aquasuite.ims.module.iam.internal.token;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String VALID_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtNjQtYnl0ZXMtbG9uZy1zby10aGlzLXNob3VsZC13b3JrIQ==";
    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        service =
                new JwtTokenService(
                        "aquasuite-be",
                        "aquasuite-api",
                        "aquasuite-auth",
                        1800,
                        1296000,
                        1296000,
                        VALID_SECRET);
        service.init();
    }

    @Test
    @DisplayName("Khởi tạo thất bại nếu secret thiếu hoặc rỗng")
    void shouldFailInitWhenSecretIsBlank() {
        JwtTokenService invalidService =
                new JwtTokenService(
                        "aquasuite-be",
                        "aquasuite-api",
                        "aquasuite-auth",
                        1800,
                        1296000,
                        1296000,
                        "   ");
        assertThatThrownBy(invalidService::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing or empty");
    }

    @Test
    @DisplayName("Khởi tạo thất bại nếu secret không phải Base64 hợp lệ")
    void shouldFailInitWhenSecretIsNotValidBase64() {
        String invalidBase64 = "This is not valid base64! @#$%^&*()_+";
        JwtTokenService invalidService =
                new JwtTokenService(
                        "aquasuite-be",
                        "aquasuite-api",
                        "aquasuite-auth",
                        1800,
                        1296000,
                        1296000,
                        invalidBase64);
        assertThatThrownBy(invalidService::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid Base64");
    }

    @Test
    @DisplayName("Khởi tạo thất bại nếu secret là Base64 hợp lệ nhưng độ dài sau decode < 64 bytes")
    void shouldFailInitWhenBase64DecodesToLessThan64Bytes() {
        String base64DecodesTo48Bytes =
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        JwtTokenService invalidService =
                new JwtTokenService(
                        "aquasuite-be",
                        "aquasuite-api",
                        "aquasuite-auth",
                        1800,
                        1296000,
                        1296000,
                        base64DecodesTo48Bytes);
        assertThatThrownBy(invalidService::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be at least 64 bytes after Base64 decode");
    }

    @Test
    @DisplayName(
            "Cấp token pair với đúng claims HS512, issuer, audience, token_type ACCESS/REFRESH, auth_version=2, sub, sid, jti")
    void shouldIssueValidTokenPair() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plusSeconds(1296000);

        JwtTokenService.TokenPair tokenPair =
                service.issueTokenPair(userId, sessionId, refreshJti, now, absoluteExpiresAt);

        assertThat(tokenPair.accessToken()).isNotBlank();
        assertThat(tokenPair.refreshToken()).isNotBlank();
        assertThat(tokenPair.refreshJti()).isEqualTo(refreshJti);
        assertThat(tokenPair.expiresInSeconds()).isLessThanOrEqualTo(1800);

        // Verify access token claims
        SignedJWT accessJwt = SignedJWT.parse(tokenPair.accessToken());
        assertThat(accessJwt.getHeader().getAlgorithm().getName()).isEqualTo("HS512");
        assertThat(accessJwt.getJWTClaimsSet().getIssuer()).isEqualTo("aquasuite-be");
        assertThat(accessJwt.getJWTClaimsSet().getAudience()).containsExactly("aquasuite-api");
        assertThat(accessJwt.getJWTClaimsSet().getStringClaim("token_type")).isEqualTo("ACCESS");
        assertThat(accessJwt.getJWTClaimsSet().getLongClaim("auth_version")).isEqualTo(2L);
        assertThat(accessJwt.getJWTClaimsSet().getSubject()).isEqualTo(userId.toString());
        assertThat(accessJwt.getJWTClaimsSet().getStringClaim("sid"))
                .isEqualTo(sessionId.toString());
        assertThat(accessJwt.getJWTClaimsSet().getJWTID()).isNotNull();

        // Verify refresh token claims
        SignedJWT refreshJwt = SignedJWT.parse(tokenPair.refreshToken());
        assertThat(refreshJwt.getHeader().getAlgorithm().getName()).isEqualTo("HS512");
        assertThat(refreshJwt.getJWTClaimsSet().getIssuer()).isEqualTo("aquasuite-be");
        assertThat(refreshJwt.getJWTClaimsSet().getAudience()).containsExactly("aquasuite-auth");
        assertThat(refreshJwt.getJWTClaimsSet().getStringClaim("token_type")).isEqualTo("REFRESH");
        assertThat(refreshJwt.getJWTClaimsSet().getLongClaim("auth_version")).isEqualTo(2L);
        assertThat(refreshJwt.getJWTClaimsSet().getSubject()).isEqualTo(userId.toString());
        assertThat(refreshJwt.getJWTClaimsSet().getStringClaim("sid"))
                .isEqualTo(sessionId.toString());
        assertThat(refreshJwt.getJWTClaimsSet().getJWTID()).isEqualTo(refreshJti.toString());
    }

    @Test
    @DisplayName("Giới hạn token exp không vượt quá absoluteExpiresAt")
    void shouldBoundExpirationByAbsoluteExpiresAt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plusSeconds(60);

        JwtTokenService.TokenPair tokenPair =
                service.issueTokenPair(userId, sessionId, now, absoluteExpiresAt);

        assertThat(tokenPair.expiresInSeconds()).isLessThanOrEqualTo(60);
        SignedJWT accessJwt = SignedJWT.parse(tokenPair.accessToken());
        assertThat(accessJwt.getJWTClaimsSet().getExpirationTime().toInstant())
                .isBeforeOrEqualTo(absoluteExpiresAt);
    }

    @Test
    @DisplayName("Xác thực refresh token hợp lệ và giải mã claims")
    void shouldParseAndValidateRefreshTokenSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plusSeconds(1296000);

        JwtTokenService.TokenPair tokenPair =
                service.issueTokenPair(userId, sessionId, refreshJti, now, absoluteExpiresAt);

        JwtTokenService.RefreshClaims claims =
                service.parseAndValidateRefreshToken(tokenPair.refreshToken());

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.sessionId()).isEqualTo(sessionId);
        assertThat(claims.jti()).isEqualTo(refreshJti);
        assertThat(claims.expiresAt()).isAfter(now);
    }

    @Test
    @DisplayName("Từ chối refresh token có chữ ký sai (AUTH_004)")
    void shouldRejectRefreshTokenWithInvalidSignature() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plusSeconds(1296000);

        String otherSecret =
                Base64.getEncoder()
                        .encodeToString(
                                "different-secret-key-that-is-at-least-64-bytes-long-for-testing-purposes-12345678"
                                        .getBytes());
        JwtTokenService otherService =
                new JwtTokenService(
                        "aquasuite-be",
                        "aquasuite-api",
                        "aquasuite-auth",
                        1800,
                        1296000,
                        1296000,
                        otherSecret);
        otherService.init();

        JwtTokenService.TokenPair foreignPair =
                otherService.issueTokenPair(userId, sessionId, now, absoluteExpiresAt);

        assertThatThrownBy(() -> service.parseAndValidateRefreshToken(foreignPair.refreshToken()))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("Từ chối access token gửi vào luồng refresh (AUTH_004)")
    void shouldRejectAccessTokenOnRefreshPath() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plusSeconds(1296000);

        JwtTokenService.TokenPair tokenPair =
                service.issueTokenPair(userId, sessionId, now, absoluteExpiresAt);

        // Pass access token instead of refresh token
        assertThatThrownBy(() -> service.parseAndValidateRefreshToken(tokenPair.accessToken()))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("Legacy token rejection: Từ chối refresh token thiếu auth_version claim")
    void shouldRejectLegacyRefreshTokenMissingAuthVersion() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        com.nimbusds.jwt.JWTClaimsSet legacyClaims =
                new com.nimbusds.jwt.JWTClaimsSet.Builder()
                        .issuer("aquasuite-be")
                        .audience(List.of("aquasuite-auth"))
                        .subject(userId.toString())
                        .claim(JwtTokenService.CLAIM_SID, sessionId.toString())
                        .claim(JwtTokenService.CLAIM_TOKEN_TYPE, "REFRESH")
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(java.util.Date.from(now))
                        .expirationTime(java.util.Date.from(now.plusSeconds(3600)))
                        .build();

        byte[] secretBytes = Base64.getDecoder().decode(VALID_SECRET);
        com.nimbusds.jwt.SignedJWT signedJWT =
                new com.nimbusds.jwt.SignedJWT(
                        new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS512),
                        legacyClaims);
        signedJWT.sign(new com.nimbusds.jose.crypto.MACSigner(secretBytes));

        assertThatThrownBy(() -> service.parseAndValidateRefreshToken(signedJWT.serialize()))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("Legacy token rejection: Từ chối access token thiếu auth_version claim")
    void shouldRejectLegacyAccessTokenMissingAuthVersion() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        com.nimbusds.jwt.JWTClaimsSet legacyClaims =
                new com.nimbusds.jwt.JWTClaimsSet.Builder()
                        .issuer("aquasuite-be")
                        .audience(List.of("aquasuite-api"))
                        .subject(userId.toString())
                        .claim(JwtTokenService.CLAIM_SID, sessionId.toString())
                        .claim(JwtTokenService.CLAIM_TOKEN_TYPE, "ACCESS")
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(java.util.Date.from(now))
                        .expirationTime(java.util.Date.from(now.plusSeconds(1800)))
                        .build();

        byte[] secretBytes = Base64.getDecoder().decode(VALID_SECRET);
        com.nimbusds.jwt.SignedJWT signedJWT =
                new com.nimbusds.jwt.SignedJWT(
                        new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS512),
                        legacyClaims);
        signedJWT.sign(new com.nimbusds.jose.crypto.MACSigner(secretBytes));

        assertThatThrownBy(() -> service.parseAndValidateAccessToken(signedJWT.serialize()))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("Phát hiện refresh token hết hạn và trả về AUTH_005")
    void shouldThrowExpiredRefreshTokenWhenExpired() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(100);
        Instant expiredAt = past.plusSeconds(10); // Expired in the past

        JwtTokenService.TokenPair expiredPair =
                service.issueTokenPair(userId, sessionId, past, expiredAt);

        assertThatThrownBy(() -> service.parseAndValidateRefreshToken(expiredPair.refreshToken()))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.EXPIRED_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("Từ chối refresh token có audience chứa chuỗi phụ hoặc không khớp chính xác")
    void shouldRejectRefreshTokenWithExtraAudience() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        com.nimbusds.jwt.JWTClaimsSet claims =
                new com.nimbusds.jwt.JWTClaimsSet.Builder()
                        .issuer("aquasuite-be")
                        .audience(List.of("aquasuite-auth", "extra-aud"))
                        .subject(userId.toString())
                        .claim(JwtTokenService.CLAIM_SID, sessionId.toString())
                        .claim(JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_REFRESH)
                        .claim(JwtTokenService.CLAIM_AUTH_VERSION, 2)
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(java.util.Date.from(now))
                        .expirationTime(java.util.Date.from(now.plusSeconds(3600)))
                        .build();

        byte[] secretBytes = Base64.getDecoder().decode(VALID_SECRET);
        com.nimbusds.jwt.SignedJWT signedJWT =
                new com.nimbusds.jwt.SignedJWT(
                        new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS512),
                        claims);
        signedJWT.sign(new com.nimbusds.jose.crypto.MACSigner(secretBytes));
        String token = signedJWT.serialize();

        assertThatThrownBy(() -> service.parseAndValidateRefreshToken(token))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("Từ chối refresh token thiếu claim iat hoặc exp")
    void shouldRejectRefreshTokenMissingIatOrExp() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        byte[] secretBytes = Base64.getDecoder().decode(VALID_SECRET);

        com.nimbusds.jwt.JWTClaimsSet claimsNoIat =
                new com.nimbusds.jwt.JWTClaimsSet.Builder()
                        .issuer("aquasuite-be")
                        .audience(List.of("aquasuite-auth"))
                        .subject(userId.toString())
                        .claim(JwtTokenService.CLAIM_SID, sessionId.toString())
                        .claim(JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_REFRESH)
                        .claim(JwtTokenService.CLAIM_AUTH_VERSION, 2)
                        .jwtID(UUID.randomUUID().toString())
                        .expirationTime(java.util.Date.from(now.plusSeconds(3600)))
                        .build();

        com.nimbusds.jwt.SignedJWT signedNoIat =
                new com.nimbusds.jwt.SignedJWT(
                        new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS512),
                        claimsNoIat);
        signedNoIat.sign(new com.nimbusds.jose.crypto.MACSigner(secretBytes));

        assertThatThrownBy(() -> service.parseAndValidateRefreshToken(signedNoIat.serialize()))
                .isInstanceOf(AppException.class)
                .satisfies(
                        e ->
                                assertThat(((AppException) e).getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }
}
