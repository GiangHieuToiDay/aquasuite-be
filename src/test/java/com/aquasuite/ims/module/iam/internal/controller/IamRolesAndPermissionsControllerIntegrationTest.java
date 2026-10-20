package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.internal.auth.dto.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.authorization.entity.Permission;
import com.aquasuite.ims.module.iam.internal.authorization.entity.Role;
import com.aquasuite.ims.module.iam.internal.authorization.repository.PermissionRepository;
import com.aquasuite.ims.module.iam.internal.authorization.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.authorization.dto.request.CreatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.authorization.dto.request.UpdatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IamRolesAndPermissionsControllerIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DataSource dataSource;

    private String ownerToken;
    private String staffToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanDbAndRedis();

        Role shopOwnerRole = roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER").orElseThrow();
        Role salesStaffRole = roleRepository.findByRoleNameIgnoreCase("SALES_STAFF").orElseThrow();

        // Create Shop Owner user
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUsername("owner_user");
        owner.setEmail("owner@aquasuite.local");
        owner.setPasswordHash(passwordEncoder.encode("Password123!"));
        owner.setStatus(UserStatus.ACTIVE);
        owner.setRoles(new HashSet<>(Set.of(shopOwnerRole)));
        userRepository.save(owner);

        // Create Sales Staff user
        User staff = new User();
        staff.setId(UUID.randomUUID());
        staff.setUsername("staff_user");
        staff.setEmail("staff@aquasuite.local");
        staff.setPasswordHash(passwordEncoder.encode("Password123!"));
        staff.setStatus(UserStatus.ACTIVE);
        staff.setRoles(new HashSet<>(Set.of(salesStaffRole)));
        userRepository.save(staff);

        ownerToken = login("owner@aquasuite.local", "Password123!");
        staffToken = login("staff@aquasuite.local", "Password123!");
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanDbAndRedis();
    }

    private void cleanDbAndRedis() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM public.role_permissions");
            stmt.executeUpdate("DELETE FROM public.user_roles");
            stmt.executeUpdate("DELETE FROM public.permissions");
            stmt.executeUpdate("DELETE FROM public.roles WHERE role_name NOT IN ('SHOP_OWNER', 'SALES_STAFF', 'ACCOUNTANT', 'TECHNICAL_STAFF', 'SHIPPER', 'CUSTOMER')");
            stmt.executeUpdate("DELETE FROM public.users");
        }
        Set<String> keys = redisTemplate.keys("auth:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = LoginRequest.builder().email(email).password(password).build();
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("access_token").asText();
    }

    @Test
    @DisplayName("AC-01, AC-17: List canonical roles returns exactly 6 roles in canonical order with permissions")
    void testListRolesSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/iam/roles")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Thành công"))
                .andExpect(jsonPath("$.data", hasSize(6)))
                .andExpect(jsonPath("$.data[0].code").value("SHOP_OWNER"))
                .andExpect(jsonPath("$.data[1].code").value("SALES_STAFF"))
                .andExpect(jsonPath("$.data[2].code").value("ACCOUNTANT"))
                .andExpect(jsonPath("$.data[3].code").value("TECHNICAL_STAFF"))
                .andExpect(jsonPath("$.data[4].code").value("SHIPPER"))
                .andExpect(jsonPath("$.data[5].code").value("CUSTOMER"))
                .andExpect(jsonPath("$.data[*].permissions[*].createdAt").doesNotExist())
                .andExpect(jsonPath("$.data[*].permissions[*].updatedAt").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Get canonical role detail by ID returns role or 404 IAM_003")
    void testGetRoleDetail() throws Exception {
        Role shopOwner = roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER").orElseThrow();

        mockMvc.perform(get("/api/v1/iam/roles/" + shopOwner.getRoleId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(shopOwner.getRoleId().toString()))
                .andExpect(jsonPath("$.data.code").value("SHOP_OWNER"))
                .andExpect(jsonPath("$.data.active").value(true));

        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/iam/roles/" + nonExistentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("IAM_003"));
    }

    @Test
    @DisplayName("AC-04, AC-17: List permissions returns stable paged DTO shape and rejects invalid parameters")
    void testListPermissionsSuccessAndValidation() throws Exception {
        // Create 2 permissions
        Permission p1 = new Permission(UUID.randomUUID(), "PERM_ALPHA", "Alpha perm");
        Permission p2 = new Permission(UUID.randomUUID(), "PERM_BETA", "Beta perm");
        permissionRepository.saveAndFlush(p1);
        permissionRepository.saveAndFlush(p2);

        // Valid search & page
        mockMvc.perform(get("/api/v1/iam/permissions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", "alpha")
                        .param("sortBy", "code")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].code").value("PERM_ALPHA"))
                .andExpect(jsonPath("$.data.items[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].updatedAt").doesNotExist());

        // Invalid query params return 400 SYS_002
        mockMvc.perform(get("/api/v1/iam/permissions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));

        mockMvc.perform(get("/api/v1/iam/permissions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));

        mockMvc.perform(get("/api/v1/iam/permissions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("sortBy", "invalid_col"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));
    }

    @Test
    @DisplayName("AC-05, AC-06, AC-07, AC-08: Create permission trims, converts uppercase, rejects duplicates and invalid codes")
    void testCreatePermission() throws Exception {
        CreatePermissionRequest request = CreatePermissionRequest.builder()
                .code("   user_create   ")
                .description("Tạo người dùng")
                .build();

        mockMvc.perform(post("/api/v1/iam/permissions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("USER_CREATE"))
                .andExpect(jsonPath("$.data.description").value("Tạo người dùng"))
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());

        // Unknown property on Create returns 400 SYS_003
        String unknownFieldPayload = """
                {
                    "code": "EXTRA_PROP_TEST",
                    "description": "desc",
                    "unexpectedField": "bad_value"
                }
                """;
        mockMvc.perform(post("/api/v1/iam/permissions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownFieldPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        // Duplicate code returns 409 IAM_005
        CreatePermissionRequest duplicateReq = CreatePermissionRequest.builder()
                .code("USER_CREATE")
                .build();

        mockMvc.perform(post("/api/v1/iam/permissions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IAM_005"));

        // Invalid code format returns 400 IAM_001
        CreatePermissionRequest invalidFormatReq = CreatePermissionRequest.builder()
                .code("123_INVALID")
                .build();

        mockMvc.perform(post("/api/v1/iam/permissions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidFormatReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IAM_001"));

        // Reserved ROLE_ namespace returns 400 IAM_002
        CreatePermissionRequest rolePrefixReq = CreatePermissionRequest.builder()
                .code("ROLE_ADMIN")
                .build();

        mockMvc.perform(post("/api/v1/iam/permissions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rolePrefixReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IAM_002"));
    }

    @Test
    @DisplayName("AC-07: Update description works, payload containing code or unknown field rejected with 400 SYS_003")
    void testUpdatePermission() throws Exception {
        Permission p = new Permission(UUID.randomUUID(), "ORDER_VIEW", "Original description");
        permissionRepository.saveAndFlush(p);

        UpdatePermissionRequest updateReq = UpdatePermissionRequest.builder()
                .description("Updated description")
                .build();

        mockMvc.perform(patch("/api/v1/iam/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("ORDER_VIEW"))
                .andExpect(jsonPath("$.data.description").value("Updated description"))
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());

        // Payload with code field rejected with 400 SYS_003
        String payloadWithCode = """
                {
                    "description": "Attempt rename",
                    "code": "ORDER_VIEW_RENAMED"
                }
                """;

        mockMvc.perform(patch("/api/v1/iam/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWithCode))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
    }

    @Test
    @DisplayName("AC-08, AC-19, AC-34: Delete unused permission returns 200 ApiResponse<Void> and JSON omits $.data")
    void testDeleteUnusedPermission() throws Exception {
        Permission p = new Permission(UUID.randomUUID(), "UNUSED_PERM", "Unused");
        permissionRepository.saveAndFlush(p);

        mockMvc.perform(delete("/api/v1/iam/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Thành công"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(permissionRepository.findById(p.getId())).isEmpty();
    }

    @Test
    @DisplayName("AC-08, AC-14: Delete assigned permission rejected with 409 IAM_006 and data preserved")
    void testDeleteAssignedPermissionFails() throws Exception {
        Permission p = new Permission(UUID.randomUUID(), "ASSIGNED_PERM", "Assigned");
        permissionRepository.saveAndFlush(p);

        Role shopOwner = roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER").orElseThrow();

        // Assign permission to role
        mockMvc.perform(put("/api/v1/iam/roles/" + shopOwner.getRoleId() + "/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Attempt delete assigned permission -> 409 IAM_006
        mockMvc.perform(delete("/api/v1/iam/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("IAM_006"));

        // Verify permission and mapping still exist
        assertThat(permissionRepository.findById(p.getId())).isPresent();
        Role reloadedOwner = roleRepository.findWithPermissionsByRoleId(shopOwner.getRoleId()).orElseThrow();
        assertThat(reloadedOwner.getPermissions()).anyMatch(perm -> perm.getId().equals(p.getId()));
    }

    @Test
    @DisplayName("AC-09, AC-15, AC-16, AC-17, AC-18: Idempotent assign and unassign permission")
    void testAssignAndUnassignPermission() throws Exception {
        Permission p = new Permission(UUID.randomUUID(), "PRODUCT_MANAGE", "Quản lý sản phẩm");
        permissionRepository.saveAndFlush(p);

        Role accountant = roleRepository.findByRoleNameIgnoreCase("ACCOUNTANT").orElseThrow();

        // 1. Assign permission
        mockMvc.perform(put("/api/v1/iam/roles/" + accountant.getRoleId() + "/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("ACCOUNTANT"))
                .andExpect(jsonPath("$.data.permissions[?(@.code == 'PRODUCT_MANAGE')]").isNotEmpty());

        // 2. Assign again (idempotent)
        mockMvc.perform(put("/api/v1/iam/roles/" + accountant.getRoleId() + "/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Unassign permission
        mockMvc.perform(delete("/api/v1/iam/roles/" + accountant.getRoleId() + "/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.permissions[?(@.code == 'PRODUCT_MANAGE')]").isEmpty());

        // 4. Unassign again (idempotent)
        mockMvc.perform(delete("/api/v1/iam/roles/" + accountant.getRoleId() + "/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("AC-03: Authenticated non-SHOP_OWNER actor receives 403 AUTH_002 on all endpoints")
    void testNonShopOwnerForbidden() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/iam/roles")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        mockMvc.perform(get("/api/v1/iam/roles/" + randomId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        mockMvc.perform(get("/api/v1/iam/permissions")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        mockMvc.perform(post("/api/v1/iam/permissions")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TEST\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        mockMvc.perform(patch("/api/v1/iam/permissions/" + randomId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"TEST\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        mockMvc.perform(delete("/api/v1/iam/permissions/" + randomId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        mockMvc.perform(put("/api/v1/iam/roles/" + randomId + "/permissions/" + randomId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        mockMvc.perform(delete("/api/v1/iam/roles/" + randomId + "/permissions/" + randomId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("AC-03: Anonymous / unauthenticated request receives 401 AUTH_001")
    void testAnonymousUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/iam/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));

        mockMvc.perform(get("/api/v1/iam/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    @DisplayName("Finding 4: Unknown properties ignored on non-WORK-010 endpoint (POST /api/v1/auth/login)")
    void testUnknownPropertyIgnoredOnExistingAuthEndpoint() throws Exception {
        String payloadWithUnknown = """
                {
                    "email": "owner@aquasuite.local",
                    "password": "Password123!",
                    "unexpectedField": "should_be_ignored"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWithUnknown))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
