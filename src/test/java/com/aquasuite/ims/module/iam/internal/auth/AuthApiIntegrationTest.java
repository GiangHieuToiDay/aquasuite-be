package com.aquasuite.ims.module.iam.internal.auth;

import com.aquasuite.ims.module.iam.internal.auth.dto.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.auth.dto.request.RefreshRequest;
import com.aquasuite.ims.module.iam.internal.authorization.entity.Role;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.module.iam.internal.authorization.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.auth.token.RedisSessionStore;
import com.aquasuite.ims.module.iam.internal.auth.token.JwtTokenService;
import com.aquasuite.ims.share.exception.AppException;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @SpyBean
    private RedisSessionStore redisSessionStore;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    private User testActiveUser;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        reset(redisSessionStore);
        // Clean up database and redis
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

        testActiveUser = new User();
        testActiveUser.setId(UUID.randomUUID());
        testActiveUser.setUsername("johndoe");
        testActiveUser.setEmail("john.doe@aquasuite.com");
        testActiveUser.setPasswordHash(passwordEncoder.encode("SecretPassword123!"));
        testActiveUser.setStatus(UserStatus.ACTIVE);
        testActiveUser.setRoles(Set.of(adminRole));
        userRepository.save(testActiveUser);
    }

    @Test
    @DisplayName("AC-01 & AC-04: Login thành công trả đúng JSON schema, username, token pair, không có user.name")
    void shouldLoginSuccessfully() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .email("  JOHN.DOE@AQUASUITE.COM  ")
                .password("SecretPassword123!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Đăng nhập thành công"))
                .andExpect(jsonPath("$.data.access_token").isString())
                .andExpect(jsonPath("$.data.refresh_token").isString())
                .andExpect(jsonPath("$.data.token_type").value("Bearer"))
                .andExpect(jsonPath("$.data.expires_in").value(1800))
                .andExpect(jsonPath("$.data.user.id").value(testActiveUser.getId().toString()))
                .andExpect(jsonPath("$.data.user.username").value("johndoe"))
                .andExpect(jsonPath("$.data.user.email").value("john.doe@aquasuite.com"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.user.name").doesNotExist())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(responseNode.path("data").path("user").has("name")).isFalse();
    }

    @Test
    @DisplayName("AC-04: Login thất bại với email không tồn tại hoặc sai password đều trả về 401 AUTH_003")
    void shouldReturnAuth003OnInvalidCredentials() throws Exception {
        // Wrong password
        LoginRequest wrongPass = LoginRequest.builder()
                .email("john.doe@aquasuite.com")
                .password("WrongPassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPass)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_CREDENTIALS.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.INVALID_CREDENTIALS.getMessage()));

        // Non-existent email
        LoginRequest nonExistent = LoginRequest.builder()
                .email("ghost@aquasuite.com")
                .password("SomePassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonExistent)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_CREDENTIALS.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.INVALID_CREDENTIALS.getMessage()));
    }

    @Test
    @DisplayName("AC-04: Login với tài khoản PENDING_ACTIVATION, INACTIVE, LOCKED trả về 403 AUTH_008")
    void shouldReturnAuth008WhenUserNotActive() throws Exception {
        for (UserStatus status : new UserStatus[]{UserStatus.PENDING_ACTIVATION, UserStatus.INACTIVE, UserStatus.LOCKED}) {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setUsername("user_" + status.name().toLowerCase());
            user.setEmail(status.name().toLowerCase() + "@aquasuite.com");
            user.setPasswordHash(passwordEncoder.encode("Secret123!"));
            user.setStatus(status);
            userRepository.save(user);

            LoginRequest req = LoginRequest.builder()
                    .email(user.getEmail())
                    .password("Secret123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(AuthErrorCode.ACCOUNT_NOT_ACTIVE.getCode()))
                    .andExpect(jsonPath("$.message").value(AuthErrorCode.ACCOUNT_NOT_ACTIVE.getMessage()));
        }
    }

    @Test
    @DisplayName("AC-10: Login validation lỗi (email sai format, rỗng) trả về 400 SYS_003 với errors")
    void shouldReturnSys003OnValidationErrors() throws Exception {
        LoginRequest invalidReq = LoginRequest.builder()
                .email("not-an-email")
                .password("")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("AC-06: Refresh token hợp lệ cấp cặp token mới")
    void shouldRefreshSuccessfully() throws Exception {
        // 1. Login first to obtain valid refresh token
        LoginRequest loginReq = LoginRequest.builder()
                .email("john.doe@aquasuite.com")
                .password("SecretPassword123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String initialRefreshToken = loginNode.path("data").path("refresh_token").asText();

        // 2. Refresh
        RefreshRequest refreshReq = RefreshRequest.builder().refreshToken(initialRefreshToken).build();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.access_token").isString())
                .andExpect(jsonPath("$.data.refresh_token").isString())
                .andExpect(jsonPath("$.data.token_type").value("Bearer"))
                .andReturn();

        JsonNode refreshNode = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newRefreshToken = refreshNode.path("data").path("refresh_token").asText();

        assertThat(newRefreshToken).isNotEqualTo(initialRefreshToken);
    }

    @Test
    @DisplayName("AC-07: Gửi lại spent refresh token sau rotation trả về 401 AUTH_006 và thu hồi session")
    void shouldDetectReplayAndRevokeSession() throws Exception {
        // 1. Login
        LoginRequest loginReq = LoginRequest.builder()
                .email("john.doe@aquasuite.com")
                .password("SecretPassword123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken1 = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("refresh_token").asText();

        // 2. Rotate once
        RefreshRequest refreshReq = RefreshRequest.builder().refreshToken(refreshToken1).build();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk());

        // 3. Send old token again -> Reused!
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.REFRESH_TOKEN_REUSED.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.REFRESH_TOKEN_REUSED.getMessage()));
    }

    @Test
    @DisplayName("AC-08: Logout thu hồi đúng current sid, access token của session đó nhận 401 AUTH_007, session khác giữ nguyên")
    void shouldLogoutSessionAndIsolateOtherSessions() throws Exception {
        // Login Session 1
        LoginRequest loginReq = LoginRequest.builder().email("john.doe@aquasuite.com").password("SecretPassword123!").build();
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk()).andReturn();

        String access1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data").path("access_token").asText();

        // Login Session 2
        MvcResult res2 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk()).andReturn();

        String access2 = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data").path("access_token").asText();

        // Logout Session 1
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + access1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng xuất thành công"))
                .andExpect(jsonPath("$.data").doesNotExist());

        // Reusing access1 now results in 401 AUTH_007 (Session Revoked)
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + access1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.SESSION_REVOKED.getCode()));

        // Session 2 is still active and can logout successfully
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + access2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đăng xuất thành công"));
    }

    @Test
    @DisplayName("CR-05: Refresh token hợp lệ về signature nhưng session không tồn tại trên Redis -> HTTP 401 AUTH_004 đầy đủ envelope")
    void shouldReturn401Auth004WhenSessionNotFoundOnRedis() throws Exception {
        UUID nonExistentSid = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair = jwtTokenService.issueTokenPair(testActiveUser.getId(), nonExistentSid, now, absoluteExpiresAt);
        RefreshRequest req = RefreshRequest.builder().refreshToken(pair.refreshToken()).build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_REFRESH_TOKEN.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.INVALID_REFRESH_TOKEN.getMessage()))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("CR-05: Refresh token có session trên Redis nhưng fingerprint không khớp và chưa spent -> HTTP 401 AUTH_004")
    void shouldReturn401Auth004WhenFingerprintMismatchesWithoutSpentMarker() throws Exception {
        UUID sid = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plusSeconds(3600);

        // Create session with fingerprint "active-fp-123"
        redisSessionStore.createSession(sid, testActiveUser.getId(), now, absoluteExpiresAt, "active-fp-123");

        // Issue token for this sid (which will have a different fingerprint)
        JwtTokenService.TokenPair pair = jwtTokenService.issueTokenPair(testActiveUser.getId(), sid, now, absoluteExpiresAt);
        RefreshRequest req = RefreshRequest.builder().refreshToken(pair.refreshToken()).build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_REFRESH_TOKEN.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.INVALID_REFRESH_TOKEN.getMessage()))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("CR-05: Refresh token đã hết hạn -> HTTP 401 AUTH_005 (expired-before-revoked precedence)")
    void shouldReturn401Auth005WhenRefreshTokenIsExpired() throws Exception {
        UUID sid = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(7200);

        JwtTokenService.TokenPair expiredPair = jwtTokenService.issueTokenPair(testActiveUser.getId(), sid, past.minusSeconds(1800), past);
        RefreshRequest req = RefreshRequest.builder().refreshToken(expiredPair.refreshToken()).build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.EXPIRED_REFRESH_TOKEN.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.EXPIRED_REFRESH_TOKEN.getMessage()))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("CR-03 & CR-05: Redis Outage / Connection failure xảy ra trong lúc refresh -> HTTP 503 AUTH_009 đầy đủ envelope")
    void shouldReturn503Auth009WhenRedisFailsDuringRefresh() throws Exception {
        UUID sid = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair = jwtTokenService.issueTokenPair(testActiveUser.getId(), sid, now, absoluteExpiresAt);
        String oldFp = jwtTokenService.computeFingerprint(pair.refreshToken());
        redisSessionStore.createSession(sid, testActiveUser.getId(), now, absoluteExpiresAt, oldFp);

        // Simulate Redis failure throwing AUTH_SERVICE_UNAVAILABLE
        doThrow(new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng"))
                .when(redisSessionStore).rotateRefresh(any(), any(), any(), any(), any(), any());

        RefreshRequest req = RefreshRequest.builder().refreshToken(pair.refreshToken()).build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getMessage()))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("CR-03: Redis Outage xảy ra trong lúc protected request -> HTTP 503 AUTH_009")
    void shouldReturn503Auth009WhenRedisFailsDuringProtectedRequest() throws Exception {
        UUID sid = UUID.randomUUID();
        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plusSeconds(3600);

        JwtTokenService.TokenPair pair = jwtTokenService.issueTokenPair(testActiveUser.getId(), sid, now, absoluteExpiresAt);

        // Simulate Redis failure on validateSession
        doThrow(new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng"))
                .when(redisSessionStore).validateSession(any(), any());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + pair.accessToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getMessage()))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("CR-03 & CR-05: Lost Response sau P3 commit — Retry token cũ qua HTTP bị phát hiện REPLAY (401 AUTH_006) và session bị thu hồi")
    void shouldHandleLostResponseRetryViaHttpAndRevokeSession() throws Exception {
        // 1. User login via HTTP
        LoginRequest loginReq = LoginRequest.builder()
                .email("john.doe@aquasuite.com")
                .password("SecretPassword123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String initialAccess = loginJson.path("data").path("access_token").asText();
        String initialRefresh = loginJson.path("data").path("refresh_token").asText();

        // 2. Client performs refresh -> Server commits P3 successfully
        RefreshRequest refreshReq = RefreshRequest.builder().refreshToken(initialRefresh).build();
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andReturn();

        // 3. Simulate lost response: Client drops the response and retries with the initial (now spent) refresh token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.REFRESH_TOKEN_REUSED.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.REFRESH_TOKEN_REUSED.getMessage()));

        // 4. Verify session is revoked: Access token now fails with 401 AUTH_007
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + initialAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.SESSION_REVOKED.getCode()));
    }

    @Test
    @DisplayName("MS-01: shouldCreateSessionAtomicallyAcrossTransportCuts — Normal create vs Before forward vs A2 vs A3")
    void shouldCreateSessionAtomicallyAcrossTransportCuts() throws Exception {
        LoginRequest loginReq = LoginRequest.builder()
                .email("john.doe@aquasuite.com")
                .password("SecretPassword123!")
                .build();

        // 1. CREATE-NORMAL: 200 SUCCESS, session created with bounded PTTL > 0
        getFaultProxy().disarm();
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isString())
                .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        String accessJwt = data.path("access_token").asText();
        UUID sid = UUID.fromString(jwtDecoder.decode(accessJwt).getClaimAsString(JwtTokenService.CLAIM_SID));

        Long pttl = getDirectControlTemplate().getExpire(RedisSessionStore.buildSessionKey(sid));
        assertThat(pttl).isNotNull().isGreaterThan(0);

        // 2. CREATE-BEFORE: Proxy drops command before forward
        getFaultProxy().armFault(FaultMode.BEFORE_FORWARD, null);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode()));

        List<ProxyEventRecord> events = getFaultProxy().getRecordedEvents();
        if (!events.isEmpty()) {
            assertThat(events.get(0).forwarded).isFalse();
        }

        // 3. CREATE-A3: Proxy withholds backend reply after commit
        getFaultProxy().armFault(FaultMode.AFTER_BACKEND_REPLY, null);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode()));

        getFaultProxy().disarm();
    }

    @Test
    @DisplayName("MS-05 & MS-08: shouldFailBeforeExactTargetCommandForward — Real proxy drop before forward produces 503 AUTH_009")
    void shouldFailBeforeExactTargetCommandForward() throws Exception {
        // Login first to get tokens
        getFaultProxy().disarm();
        LoginRequest loginReq = LoginRequest.builder()
                .email("john.doe@aquasuite.com")
                .password("SecretPassword123!")
                .build();

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginData = objectMapper.readTree(loginRes.getResponse().getContentAsString()).path("data");
        String accessToken = loginData.path("access_token").asText();
        String refreshToken = loginData.path("refresh_token").asText();

        // 1. Arm BEFORE_FORWARD on refresh
        getFaultProxy().armFault(FaultMode.BEFORE_FORWARD, null);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RefreshRequest.builder().refreshToken(refreshToken).build())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getMessage()))
                .andExpect(jsonPath("$.timestamp").isString());

        // 2. Arm BEFORE_FORWARD on logout
        getFaultProxy().armFault(FaultMode.BEFORE_FORWARD, null);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getMessage()))
                .andExpect(jsonPath("$.timestamp").isString());

        getFaultProxy().disarm();
    }

    @Test
    @DisplayName("MS-06: shouldWithholdCompletedBackendReply — Proxy withholds backend reply after commit producing 503 AUTH_009")
    void shouldWithholdCompletedBackendReply() throws Exception {
        getFaultProxy().disarm();
        LoginRequest loginReq = LoginRequest.builder()
                .email("john.doe@aquasuite.com")
                .password("SecretPassword123!")
                .build();

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginData = objectMapper.readTree(loginRes.getResponse().getContentAsString()).path("data");
        String accessToken = loginData.path("access_token").asText();
        String refreshToken = loginData.path("refresh_token").asText();

        // Pre-load rotate script so Redis has it in cache and executes EVALSHA directly without NOSCRIPT fallback
        String rotateScript = new String(new ClassPathResource("redis/rotate-refresh.lua").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String rotateSha = computeSha1Hex(rotateScript);
        getDirectControlTemplate().execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.scriptingCommands().scriptLoad(rotateScript.getBytes(StandardCharsets.UTF_8));
            return null;
        });

        // Arm AFTER_BACKEND_REPLY specifically on rotateRefresh Lua: Backend commits P3, but client reply is withheld
        getFaultProxy().armFault(FaultMode.AFTER_BACKEND_REPLY, rotateSha);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RefreshRequest.builder().refreshToken(refreshToken).build())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode()));

        getFaultProxy().disarm();
        JwtTokenService.RefreshClaims parsed = jwtTokenService.parseAndValidateRefreshToken(refreshToken);

        // Retry with now-spent token: Replay detected (401 AUTH_006) or session revoked due to replay (401 AUTH_007)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RefreshRequest.builder().refreshToken(refreshToken).build())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.isOneOf(
                        AuthErrorCode.REFRESH_TOKEN_REUSED.getCode(),
                        AuthErrorCode.SESSION_REVOKED.getCode()
                )));
        assertThat(getDirectControlTemplate().opsForValue().get(RedisSessionStore.buildRevokedKey(parsed.sessionId()))).isNotNull();
    }

    @Test
    @DisplayName("BLOCKING-01: shouldFailClosedWith503WhenLuaRotationReturnsNull — Null Lua rotation result fails closed as 503 AUTH_009 at API boundary")
    void shouldFailClosedWith503WhenLuaRotationReturnsNull() throws Exception {
        getFaultProxy().disarm();
        LoginRequest loginReq = LoginRequest.builder()
                .email("john.doe@aquasuite.com")
                .password("SecretPassword123!")
                .build();

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginData = objectMapper.readTree(loginRes.getResponse().getContentAsString()).path("data");
        String refreshToken = loginData.path("refresh_token").asText();

        // Mock redisSessionStore.rotateRefresh to simulate null Lua result throwing AUTH_SERVICE_UNAVAILABLE
        org.mockito.Mockito.doThrow(new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng"))
                .when(redisSessionStore).rotateRefresh(any(), any(), any(), any(), any(), any());

        // Perform refresh request -> Must return HTTP 503 and AUTH_009, not 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RefreshRequest.builder().refreshToken(refreshToken).build())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode()));

        // Verify session was NOT corrupted or unintended new token issued
        org.mockito.Mockito.reset(redisSessionStore);
    }

    @Test
    @DisplayName("FEATURE-009: Development owner account login with unchanged LoginResponse contract")
    void shouldLoginSuccessfullyWithDevelopmentOwnerAccount() throws Exception {
        Role shopOwnerRole = roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER")
                .orElseGet(() -> roleRepository.save(new Role(UUID.randomUUID(), "SHOP_OWNER", "Chủ cửa hàng", true, null, Set.of())));

        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUsername("owner_test");
        owner.setEmail("owner@aquasuite.local");
        owner.setPasswordHash(passwordEncoder.encode("DevOwnerSecretPassword123!"));
        owner.setStatus(UserStatus.ACTIVE);
        owner.setRoles(Set.of(shopOwnerRole));
        userRepository.save(owner);

        LoginRequest req = LoginRequest.builder()
                .email("owner@aquasuite.local")
                .password("DevOwnerSecretPassword123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.access_token").isString())
                .andExpect(jsonPath("$.data.refresh_token").isString())
                .andExpect(jsonPath("$.data.user.username").value("owner_test"))
                .andExpect(jsonPath("$.data.user.email").value("owner@aquasuite.local"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("SHOP_OWNER"));
    }
}
