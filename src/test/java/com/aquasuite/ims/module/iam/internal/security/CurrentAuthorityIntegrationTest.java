package com.aquasuite.ims.module.iam.internal.security;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.repository.PermissionRepository;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(CurrentAuthorityIntegrationTest.TestSecurityController.class)
class CurrentAuthorityIntegrationTest extends AuthIntegrationTestSupport {

    @RestController
    @RequestMapping("/api/v1/test-auth")
    static class TestSecurityController {

        @GetMapping("/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        public ApiResponse<String> adminOnly() {
            return ApiResponse.ok("admin-success");
        }

        @GetMapping("/perm-role-admin")
        @PreAuthorize("hasAuthority('ROLE_ADMIN')")
        public ApiResponse<String> permRoleAdmin() {
            return ApiResponse.ok("perm-role-admin-success");
        }

        @GetMapping("/user-read")
        @PreAuthorize("hasAuthority('user:read')")
        public ApiResponse<String> userRead() {
            return ApiResponse.ok("user-read-success");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        roleRepository.deleteAll(roleRepository.findAll().stream()
                .filter(r -> !Set.of("SHOP_OWNER", "SALES_STAFF", "ACCOUNTANT", "TECHNICAL_STAFF", "SHIPPER", "CUSTOMER").contains(r.getRoleName()))
                .toList());
        permissionRepository.deleteAll(permissionRepository.findAll().stream()
                .filter(p -> !p.getCode().startsWith("CATALOG_"))
                .toList());
        Set<String> keys = redisTemplate.keys("auth:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("AC-05: Thay đổi trạng thái tài khoản sang LOCKED trong DB có hiệu lực ngay ở request kế tiếp (403 AUTH_008)")
    void shouldRevokeAccessImmediatelyWhenUserStatusChangesInDatabase() throws Exception {
        Role role = new Role(UUID.randomUUID(), "ADMIN", "Admin", true, null, Set.of());
        roleRepository.save(role);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("bob");
        user.setEmail("bob@aquasuite.com");
        user.setPasswordHash(passwordEncoder.encode("Pass123!"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        userRepository.save(user);

        // 1. Login
        LoginRequest req = LoginRequest.builder().email("bob@aquasuite.com").password("Pass123!").build();
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();

        String accessToken = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("access_token").asText();

        // 2. Access protected endpoint -> 200 OK
        mockMvc.perform(get("/api/v1/test-auth/admin-only")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 3. Directly lock user in PostgreSQL
        user.setStatus(UserStatus.LOCKED);
        userRepository.save(user);

        // 4. Next protected request with same valid token is rejected with 403 AUTH_008!
        mockMvc.perform(get("/api/v1/test-auth/admin-only")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.ACCOUNT_NOT_ACTIVE.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.ACCOUNT_NOT_ACTIVE.getMessage()));
    }

    @Test
    @DisplayName("AC-05: Vô hiệu hóa Role (isActive = false) trong DB có hiệu lực ngay ở request kế tiếp (403 AUTH_002)")
    void shouldStripAuthoritiesImmediatelyWhenRoleDeactivated() throws Exception {
        Role role = new Role(UUID.randomUUID(), "ADMIN", "Admin", true, null, Set.of());
        roleRepository.save(role);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("charlie");
        user.setEmail("charlie@aquasuite.com");
        user.setPasswordHash(passwordEncoder.encode("Pass123!"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        userRepository.save(user);

        LoginRequest req = LoginRequest.builder().email("charlie@aquasuite.com").password("Pass123!").build();
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();

        String accessToken = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("access_token").asText();

        // 1. Success
        mockMvc.perform(get("/api/v1/test-auth/admin-only")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 2. Deactivate role in DB
        role.setActive(false);
        roleRepository.save(role);

        // 3. Next request is denied with 403 AUTH_002
        mockMvc.perform(get("/api/v1/test-auth/admin-only")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.ACCESS_DENIED.getCode()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.ACCESS_DENIED.getMessage()));
    }

    @Test
    @DisplayName("AC-09: User chỉ có permission (không có role ADMIN) thì cả hasRole('ADMIN') và hasAuthority('ROLE_ADMIN') đều bị từ chối 403; database CHECK constraint từ chối lưu permission có prefix ROLE_")
    void shouldPreventPrivilegeEscalationFromRoleAdminPermission() throws Exception {
        // 1. Verify DB CHECK constraint rejects inserting permission starting with ROLE_
        Permission permRoleAdmin = new Permission(UUID.randomUUID(), "ROLE_ADMIN", "Malicious permission code");
        assertThatThrownBy(() -> permissionRepository.saveAndFlush(permRoleAdmin))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // 2. User with viewer role and standard permission cannot access admin-only endpoint
        Permission normalPerm = new Permission(UUID.randomUUID(), "ORDER_VIEW_ONLY", "Normal permission");
        permissionRepository.saveAndFlush(normalPerm);

        Role viewerRole = new Role(UUID.randomUUID(), "VIEWER", "Viewer", true, null, Set.of(normalPerm));
        roleRepository.save(viewerRole);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("david");
        user.setEmail("david@aquasuite.com");
        user.setPasswordHash(passwordEncoder.encode("Pass123!"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(viewerRole));
        userRepository.save(user);

        LoginRequest req = LoginRequest.builder().email("david@aquasuite.com").password("Pass123!").build();
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();

        String accessToken = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("access_token").asText();

        // Both endpoints must deny access with 403 AUTH_002
        mockMvc.perform(get("/api/v1/test-auth/admin-only")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.ACCESS_DENIED.getCode()));

        mockMvc.perform(get("/api/v1/test-auth/perm-role-admin")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.ACCESS_DENIED.getCode()));
    }
}
