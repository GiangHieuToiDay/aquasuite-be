package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.service.AuthService;
import com.aquasuite.ims.module.iam.internal.service.impl.CurrentAuthoritiesService;
import com.aquasuite.ims.module.iam.internal.token.JwtTokenService;
import com.aquasuite.ims.module.iam.internal.token.RedisSessionStore;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** WORK-014: contract, security and failure-path tests for {@code GET /api/v1/auth/me}. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CurrentUserApiIntegrationTest extends AuthIntegrationTestSupport {

    private static final String ME = "/api/v1/auth/me";
    private static final String PASSWORD = "Password123!";
    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtNjQtYnl0ZXMtbG9uZy1zby10aGlzLXNob3VsZC13b3JrIQ==";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JwtTokenService jwtTokenService;

    @SpyBean private RedisSessionStore redisSessionStore;
    @SpyBean private CurrentAuthoritiesService currentAuthoritiesService;
    @SpyBean private AuthService authService;

    private User activeUser;
    private Role managerRole;

    @BeforeEach
    void setUp() {
        reset(redisSessionStore, currentAuthoritiesService, authService);
        userRepository.deleteAll();
        roleRepository.deleteAll(
                roleRepository.findAll().stream()
                        .filter(
                                r ->
                                        !Set.of(
                                                        "SHOP_OWNER",
                                                        "SALES_STAFF",
                                                        "ACCOUNTANT",
                                                        "TECHNICAL_STAFF",
                                                        "SHIPPER",
                                                        "CUSTOMER")
                                                .contains(r.getRoleName()))
                        .toList());
        Set<String> keys = redisTemplate.keys("auth:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        managerRole = roleRepository.save(role("MANAGER", true));

        activeUser = new User();
        activeUser.setId(UUID.randomUUID());
        activeUser.setUsername("alice");
        activeUser.setEmail("alice@aquasuite.com");
        activeUser.setPasswordHash(passwordEncoder.encode(PASSWORD));
        activeUser.setStatus(UserStatus.ACTIVE);
        activeUser.setRoles(new HashSet<>(Set.of(managerRole)));
        activeUser = userRepository.save(activeUser);
    }

    @Test
    @DisplayName("AC-01 & AC-11: access JWT hop le tra 200 SUCCESS dung contract va no-store")
    void shouldReturnCurrentUserProfile() throws Exception {
        String access = loginAccessToken();

        MvcResult result =
                mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.code").value("SUCCESS"))
                        .andExpect(jsonPath("$.timestamp").exists())
                        .andExpect(jsonPath("$.data.id").value(activeUser.getId().toString()))
                        .andExpect(jsonPath("$.data.username").value("alice"))
                        .andExpect(jsonPath("$.data.email").value("alice@aquasuite.com"))
                        .andExpect(jsonPath("$.data.roles.length()").value(1))
                        .andExpect(jsonPath("$.data.roles[0]").value("MANAGER"))
                        .andExpect(jsonPath("$.errors").doesNotExist())
                        .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.CACHE_CONTROL))
                .containsExactly("no-store");

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        List<String> fields = new ArrayList<>();
        data.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "username", "email", "roles");
    }

    @Test
    @DisplayName("AC-09: response va log khong lo JWT, session ID, password hash hoac permissions")
    void shouldNotLeakSensitiveData(CapturedOutput output) throws Exception {
        String access = loginAccessToken();
        String sid = sidOf(access);

        String body =
                mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(body)
                .doesNotContain(access)
                .doesNotContain(sid)
                .doesNotContain(activeUser.getPasswordHash())
                .doesNotContain("permission")
                .doesNotContain("token");
        assertThat(output.getAll()).doesNotContain(access);
    }

    @Test
    @DisplayName("AC-07: user hop le khong co active role nhan roles: []")
    void shouldReturnEmptyRolesArray() throws Exception {
        String access = loginAccessToken();
        managerRole.setActive(false);
        roleRepository.save(managerRole);

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.roles").isEmpty());
    }

    @Test
    @DisplayName("AC-06: roles phan anh PostgreSQL hien hanh o request ke tiep")
    void shouldReflectCurrentRolesOnNextRequest() throws Exception {
        String access = loginAccessToken();

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(jsonPath("$.data.roles.length()").value(1))
                .andExpect(jsonPath("$.data.roles[0]").value("MANAGER"));

        Role zeta = roleRepository.save(role("zeta_role", true));
        Role alpha = roleRepository.save(role(" ROLE_alpha ", true));
        Role inactive = roleRepository.save(role("DISABLED_ROLE", false));
        Role collisionA = roleRepository.save(role("ROLE_DUP", true));
        Role collisionB = roleRepository.save(role("dup", true));
        managerRole.setActive(false);
        roleRepository.save(managerRole);
        User reloaded = userRepository.findById(activeUser.getId()).orElseThrow();
        reloaded.setRoles(
                new HashSet<>(List.of(managerRole, zeta, alpha, inactive, collisionA, collisionB)));
        userRepository.save(reloaded);

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(2))
                .andExpect(jsonPath("$.data.roles[0]").value("ALPHA"))
                .andExpect(jsonPath("$.data.roles[1]").value("ZETA_ROLE"));

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                LoginRequest.builder()
                                                        .email(activeUser.getEmail())
                                                        .password(PASSWORD)
                                                        .build())))
                .andExpect(jsonPath("$.data.user.roles.length()").value(2))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ALPHA"))
                .andExpect(jsonPath("$.data.user.roles[1]").value("ZETA_ROLE"));
    }

    @Test
    @DisplayName("AC-02 & AC-11: khong co access token tra 401 AUTH_001, khong co data, no-store")
    void shouldRejectMissingToken() throws Exception {
        expectAuthError(mockMvc.perform(get(ME)), status().isUnauthorized(), "AUTH_001");
        verify(authService, never()).getCurrentUser(any());
    }

    @Test
    @DisplayName("AC-03: token malformed, sai signature/audience/type hoac het han tra 401 AUTH_001")
    void shouldRejectInvalidTokensBeforeController() throws Exception {
        String access = loginAccessToken();
        String refresh = lastRefreshToken;
        String tampered = access.substring(0, access.length() - 5) + "xxxxx";

        UUID sid = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(200);
        String expired =
                jwtTokenService
                        .issueTokenPair(activeUser.getId(), sid, past, past.plusSeconds(10))
                        .accessToken();
        String wrongAudience = signedAccessToken(List.of("other-api"), sid);

        for (String token : List.of("not-a-jwt", tampered, refresh, expired, wrongAudience)) {
            expectAuthError(
                    mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)),
                    status().isUnauthorized(),
                    "AUTH_001");
        }
        verify(authService, never()).getCurrentUser(any());
    }

    @Test
    @DisplayName("AC-04 & AC-12: token con han thuoc revoked session tra 401 AUTH_007 va xoa khoi user-session index")
    void shouldRejectRevokedSession() throws Exception {
        String access = loginAccessToken();
        UUID sid = jwtTokenService.parseAndValidateAccessToken(access).sessionId();
        String userSessionsKey = RedisSessionStore.buildUserSessionsKey(activeUser.getId());
        assertThat(redisTemplate.opsForSet().isMember(userSessionsKey, sid.toString())).isTrue();

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk());

        assertThat(redisTemplate.opsForSet().isMember(userSessionsKey, sid.toString())).isFalse();

        expectAuthError(
                mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access)),
                status().isUnauthorized(),
                AuthErrorCode.SESSION_REVOKED.getCode());
        verify(authService, never()).getCurrentUser(any());
    }

    @Test
    @DisplayName("AC-05: user khong ton tai tra 401 AUTH_001")
    void shouldRejectDeletedUser() throws Exception {
        String access = loginAccessToken();
        userRepository.deleteById(activeUser.getId());

        expectAuthError(
                mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access)),
                status().isUnauthorized(),
                CommonErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("AC-05: user khong con ACTIVE tra 403 AUTH_008")
    void shouldRejectInactiveUser() throws Exception {
        String access = loginAccessToken();
        User reloaded = userRepository.findById(activeUser.getId()).orElseThrow();
        reloaded.setStatus(UserStatus.LOCKED);
        userRepository.save(reloaded);

        expectAuthError(
                mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access)),
                status().isForbidden(),
                AuthErrorCode.ACCOUNT_NOT_ACTIVE.getCode());
    }

    @Test
    @DisplayName("AC-05 & AC-11: user bien mat giua security chain va controller tra 401 AUTH_001, no-store")
    void shouldRejectUserMissingAtProfileResolution() throws Exception {
        String access = loginAccessToken();
        doCallRealMethod()
                .doReturn(Optional.empty())
                .when(currentAuthoritiesService)
                .loadCurrentUser(activeUser.getId());

        expectAuthError(
                mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access)),
                status().isUnauthorized(),
                CommonErrorCode.UNAUTHORIZED.getCode());
        verify(authService).getCurrentUser(any());
    }

    @Test
    @DisplayName("AC-05 & AC-11: user bi khoa giua security chain va controller tra 403 AUTH_008, no-store")
    void shouldRejectUserDeactivatedAtProfileResolution() throws Exception {
        String access = loginAccessToken();
        User locked = userRepository.findById(activeUser.getId()).orElseThrow();
        locked.setStatus(UserStatus.INACTIVE);
        locked.setRoles(new HashSet<>());
        doCallRealMethod()
                .doReturn(Optional.of(locked))
                .when(currentAuthoritiesService)
                .loadCurrentUser(activeUser.getId());

        expectAuthError(
                mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access)),
                status().isForbidden(),
                AuthErrorCode.ACCOUNT_NOT_ACTIVE.getCode());
        verify(authService).getCurrentUser(any());
    }

    @Test
    @DisplayName("AC-08 & AC-11: PostgreSQL outage tai profile resolution tra 503 AUTH_009, no-store")
    void shouldFailClosedOnDatabaseOutageAtProfileResolution() throws Exception {
        String access = loginAccessToken();
        doCallRealMethod()
                .doThrow(new DataAccessResourceFailureException("db down"))
                .when(currentAuthoritiesService)
                .loadCurrentUser(activeUser.getId());

        expectAuthError(
                mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access)),
                status().isServiceUnavailable(),
                AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    @DisplayName("AC-08: PostgreSQL outage trong security chain tra 503 AUTH_009")
    void shouldFailClosedOnDatabaseOutageInSecurityChain() throws Exception {
        String access = loginAccessToken();
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(currentAuthoritiesService)
                .loadCurrentUser(activeUser.getId());

        expectAuthError(
                mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access)),
                status().isServiceUnavailable(),
                AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode());
        verify(authService, never()).getCurrentUser(any());
    }

    @Test
    @DisplayName("AC-08: Redis outage tra 503 AUTH_009 va khong tra profile")
    void shouldFailClosedOnRedisOutage() throws Exception {
        String access = loginAccessToken();
        doThrow(new AppException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE))
                .when(redisSessionStore)
                .validateSession(any(), any());
        try {
            expectAuthError(
                    mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access)),
                    status().isServiceUnavailable(),
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode());
            verify(authService, never()).getCurrentUser(any());
        } finally {
            reset(redisSessionStore);
        }
    }

    @Test
    @DisplayName("Security: doc profile khong tao/rotate/revoke session Redis")
    void shouldNotMutateRedisSession() throws Exception {
        String access = loginAccessToken();
        reset(redisSessionStore);

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk());

        verify(redisSessionStore).validateSession(any(), any());
        verifyNoMoreInteractions(redisSessionStore);
    }

    private void expectAuthError(ResultActions actions, ResultMatcher status, String code)
            throws Exception {
        MvcResult result =
                actions.andExpect(status)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.code").value(code))
                        .andExpect(jsonPath("$.data").doesNotExist())
                        .andReturn();
        String cacheControl = String.join(",", result.getResponse().getHeaders(HttpHeaders.CACHE_CONTROL));
        assertThat(cacheControl).contains("no-store");
    }

    private String lastRefreshToken;

    private String loginAccessToken() throws Exception {
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        LoginRequest.builder()
                                                                .email(activeUser.getEmail())
                                                                .password(PASSWORD)
                                                                .build())))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        lastRefreshToken = data.path("refresh_token").asText();
        return data.path("access_token").asText();
    }

    private String sidOf(String token) throws Exception {
        return com.nimbusds.jwt.SignedJWT.parse(token)
                .getJWTClaimsSet()
                .getStringClaim(JwtTokenService.CLAIM_SID);
    }

    private String signedAccessToken(List<String> audience, UUID sid) throws Exception {
        Instant now = Instant.now();
        com.nimbusds.jwt.JWTClaimsSet claims =
                new com.nimbusds.jwt.JWTClaimsSet.Builder()
                        .issuer("aquasuite-be")
                        .audience(audience)
                        .subject(activeUser.getId().toString())
                        .claim(JwtTokenService.CLAIM_SID, sid.toString())
                        .claim(JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_ACCESS)
                        .claim(JwtTokenService.CLAIM_AUTH_VERSION, JwtTokenService.AUTH_VERSION_V2)
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(java.util.Date.from(now))
                        .expirationTime(java.util.Date.from(now.plusSeconds(1800)))
                        .build();
        com.nimbusds.jwt.SignedJWT jwt =
                new com.nimbusds.jwt.SignedJWT(
                        new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS512),
                        claims);
        jwt.sign(new com.nimbusds.jose.crypto.MACSigner(java.util.Base64.getDecoder().decode(TEST_SECRET)));
        return jwt.serialize();
    }

    private Role role(String name, boolean active) {
        return new Role(UUID.randomUUID(), name, null, active, null, new HashSet<>());
    }
}
