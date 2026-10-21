package com.aquasuite.ims.module.iam.internal.security;

import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.RefreshRequest;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.token.JwtTokenService;
import com.aquasuite.ims.module.iam.internal.token.RedisSessionStore;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JwtSecurityIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;

    @Autowired private UserRepository userRepository;

    @Autowired private RoleRepository roleRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private StringRedisTemplate redisTemplate;

    @Autowired private JwtTokenService jwtTokenService;

    @Autowired private RedisSessionStore redisSessionStore;

    private User activeUser;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        roleRepository.deleteAll(roleRepository.findAll().stream()
                .filter(r -> !Set.of("SHOP_OWNER", "SALES_STAFF", "ACCOUNTANT", "TECHNICAL_STAFF", "SHIPPER", "CUSTOMER").contains(r.getRoleName()))
                .toList());
        Set<String> keys = redisTemplate.keys("auth:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        adminRole = new Role(UUID.randomUUID(), "ADMIN", "Administrator", true, null, Set.of());
        roleRepository.save(adminRole);

        activeUser = new User();
        activeUser.setId(UUID.randomUUID());
        activeUser.setUsername("alice");
        activeUser.setEmail("alice@aquasuite.com");
        activeUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        activeUser.setStatus(UserStatus.ACTIVE);
        activeUser.setRoles(Set.of(adminRole));
        userRepository.save(activeUser);
    }

    @Test
    @DisplayName(
            "AC-02: Token swap bị chặn — refresh token không được dùng làm access token và ngược lại")
    void shouldPreventTokenSwap() throws Exception {
        LoginRequest req =
                LoginRequest.builder()
                        .email("alice@aquasuite.com")
                        .password("Password123!")
                        .build();
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        String accessToken = data.path("access_token").asText();
        String refreshToken = data.path("refresh_token").asText();

        // Pass refresh token to protected route -> 401 AUTH_001
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()));

        // Pass access token to refresh route -> 401 AUTH_004
        RefreshRequest refreshReq = RefreshRequest.builder().refreshToken(accessToken).build();
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_REFRESH_TOKEN.getCode()));
    }

    @Test
    @DisplayName(
            "AC-02: Access token có signature bị sửa đổi hoặc claims sai bị từ chối 401 AUTH_001")
    void shouldRejectTamperedAccessToken() throws Exception {
        LoginRequest req =
                LoginRequest.builder()
                        .email("alice@aquasuite.com")
                        .password("Password123!")
                        .build();
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk())
                        .andReturn();

        String accessToken =
                objectMapper
                        .readTree(res.getResponse().getContentAsString())
                        .path("data")
                        .path("access_token")
                        .asText();
        String tamperedToken = accessToken.substring(0, accessToken.length() - 5) + "xxxxx";

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("CR-06: Access token có audience chứa chuỗi phụ bị từ chối 401 AUTH_001")
    void shouldRejectAccessTokenWithExtraAudience() throws Exception {
        UUID sid = UUID.randomUUID();
        Instant now = Instant.now();
        com.nimbusds.jwt.JWTClaimsSet claims =
                new com.nimbusds.jwt.JWTClaimsSet.Builder()
                        .issuer("aquasuite-be")
                        .audience(List.of("aquasuite-api", "extra-aud"))
                        .subject(activeUser.getId().toString())
                        .claim(JwtTokenService.CLAIM_SID, sid.toString())
                        .claim(JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_ACCESS)
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(java.util.Date.from(now))
                        .expirationTime(java.util.Date.from(now.plusSeconds(1800)))
                        .build();

        byte[] secretBytes =
                java.util.Base64.getDecoder()
                        .decode(
                                "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtNjQtYnl0ZXMtbG9uZy1zby10aGlzLXNob3VsZC13b3JrIQ==");
        com.nimbusds.jwt.SignedJWT signedJWT =
                new com.nimbusds.jwt.SignedJWT(
                        new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS512),
                        claims);
        signedJWT.sign(new com.nimbusds.jose.crypto.MACSigner(secretBytes));

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + signedJWT.serialize()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("CR-06: Access token thiếu claim iat hoặc exp bị từ chối 401 AUTH_001")
    void shouldRejectAccessTokenMissingIatOrExp() throws Exception {
        UUID sid = UUID.randomUUID();
        Instant now = Instant.now();
        byte[] secretBytes =
                java.util.Base64.getDecoder()
                        .decode(
                                "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtNjQtYnl0ZXMtbG9uZy1zby10aGlzLXNob3VsZC13b3JrIQ==");

        // Missing iat
        com.nimbusds.jwt.JWTClaimsSet claimsNoIat =
                new com.nimbusds.jwt.JWTClaimsSet.Builder()
                        .issuer("aquasuite-be")
                        .audience(List.of("aquasuite-api"))
                        .subject(activeUser.getId().toString())
                        .claim(JwtTokenService.CLAIM_SID, sid.toString())
                        .claim(JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_ACCESS)
                        .jwtID(UUID.randomUUID().toString())
                        .expirationTime(java.util.Date.from(now.plusSeconds(1800)))
                        .build();

        com.nimbusds.jwt.SignedJWT signedNoIat =
                new com.nimbusds.jwt.SignedJWT(
                        new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS512),
                        claimsNoIat);
        signedNoIat.sign(new com.nimbusds.jose.crypto.MACSigner(secretBytes));

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + signedNoIat.serialize()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName(
            "AC-16: Thứ tự phân loại lỗi — Access token hết hạn có revoked tombstone vẫn trả về 401 AUTH_001 (không phải AUTH_007)")
    void shouldPrioritizeExpiredOverRevokedForAccess() throws Exception {
        UUID sid = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(200);
        Instant expiredAt = past.plusSeconds(10); // Expired in past

        // Issue expired token
        JwtTokenService.TokenPair expiredPair =
                jwtTokenService.issueTokenPair(activeUser.getId(), sid, past, expiredAt);

        // Record revoked tombstone in Redis for this sid (create session first so revoke succeeds
        // per invariant L-03)
        redisSessionStore.createSession(
                sid, activeUser.getId(), past, Instant.now().plusSeconds(3600), "fp-expired");
        redisSessionStore.revokeSession(sid, activeUser.getId(), "LOGOUT");

        // Request with expired token on protected route -> must be 401 AUTH_001 (Expired), NOT
        // AUTH_007
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + expiredPair.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName(
            "AC-16: Thứ tự phân loại lỗi — Refresh token hết hạn có revoked tombstone vẫn trả về 401 AUTH_005 (không phải AUTH_007)")
    void shouldPrioritizeExpiredOverRevokedForRefresh() throws Exception {
        UUID sid = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(200);
        Instant expiredAt = past.plusSeconds(10); // Expired in past

        JwtTokenService.TokenPair expiredPair =
                jwtTokenService.issueTokenPair(activeUser.getId(), sid, past, expiredAt);

        // Record revoked tombstone in Redis (create session first so revoke succeeds per invariant
        // L-03)
        redisSessionStore.createSession(
                sid,
                activeUser.getId(),
                past,
                Instant.now().plusSeconds(3600),
                "fp-expired-refresh");
        redisSessionStore.revokeSession(sid, activeUser.getId(), "LOGOUT");

        // Request with expired refresh token -> must be 401 AUTH_005 (Expired), NOT AUTH_007
        RefreshRequest req =
                RefreshRequest.builder().refreshToken(expiredPair.refreshToken()).build();
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.EXPIRED_REFRESH_TOKEN.getCode()));
    }

    @Test
    @DisplayName("AC-16: Token còn hiệu lực thời gian thuộc session đã revoked trả về 401 AUTH_007")
    void shouldReturnAuth007ForTimeValidTokenOfRevokedSession() throws Exception {
        LoginRequest req =
                LoginRequest.builder()
                        .email("alice@aquasuite.com")
                        .password("Password123!")
                        .build();
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        String access = data.path("access_token").asText();
        String refresh = data.path("refresh_token").asText();

        // Logout revokes session
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        // Now both access and refresh (which are time-valid) return 401 AUTH_007
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.SESSION_REVOKED.getCode()));

        RefreshRequest refreshReq = RefreshRequest.builder().refreshToken(refresh).build();
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.SESSION_REVOKED.getCode()));
    }

    @Test
    @DisplayName("CR-02 & AC-12: Khởi tạo ứng dụng thất bại khi thiếu JWT secret trong cấu hình")
    void shouldFailApplicationStartupWhenJwtSecretIsMissing() {
        org.springframework.boot.builder.SpringApplicationBuilder builder =
                new org.springframework.boot.builder.SpringApplicationBuilder(
                                com.aquasuite.ims.module.iam.internal.auth.token.JwtTokenService
                                        .class)
                        .web(org.springframework.boot.WebApplicationType.NONE)
                        .registerShutdownHook(false)
                        .properties(
                                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                                "aquasuite.security.jwt.secret=");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> builder.run("--aquasuite.security.jwt.secret="))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .satisfies(
                        e -> {
                            Throwable root = org.assertj.core.util.Throwables.getRootCause(e);
                            org.assertj.core.api.Assertions.assertThat(root.getMessage())
                                    .contains("JWT signing secret is missing or empty");
                        });
    }
}
