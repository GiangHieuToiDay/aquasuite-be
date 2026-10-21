package com.aquasuite.ims.module.catalog.internal.security;

import com.aquasuite.ims.module.catalog.internal.payload.request.CreateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.request.CreateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.request.UpdateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.request.UpdateCategoryRequest;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogSecurityAndAuthorityIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DataSource dataSource;

    private String shopOwnerToken;
    private String restrictedToken;
    private UUID testRoleId;
    private UUID catReadPermId;
    private UUID catCreatePermId;

    @BeforeEach
    void setUp() throws Exception {
        cleanData();

        UUID ownerId = UUID.randomUUID();
        UUID restrictedUserId = UUID.randomUUID();
        testRoleId = UUID.randomUUID();
        catReadPermId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        catCreatePermId = UUID.fromString("20000000-0000-4000-8000-000000000002");

        String encodedPass = passwordEncoder.encode("Password123!");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // 1. Create SHOP_OWNER user via SQL
            stmt.executeUpdate("INSERT INTO public.users (id, username, email, password_hash, status, created_at) " +
                    "VALUES ('" + ownerId + "', 'sec_owner', 'sec_owner@aquasuite.local', '" + encodedPass + "', 'ACTIVE', CURRENT_TIMESTAMP)");
            stmt.executeUpdate("INSERT INTO public.user_roles (user_id, role_id) " +
                    "SELECT '" + ownerId + "', role_id FROM public.roles WHERE role_name = 'SHOP_OWNER'");

            // 2. Create custom role for testing dynamic grant/revoke
            stmt.executeUpdate("INSERT INTO public.roles (role_id, role_name, description, is_active) VALUES ('" +
                    testRoleId + "', 'TEST_CATALOG_ROLE', 'Role for testing authority dynamic reload', true)");

            // 3. Create restricted user with no initial permissions
            stmt.executeUpdate("INSERT INTO public.users (id, username, email, password_hash, status, created_at) " +
                    "VALUES ('" + restrictedUserId + "', 'restricted_user', 'restricted@aquasuite.local', '" + encodedPass + "', 'ACTIVE', CURRENT_TIMESTAMP)");
            stmt.executeUpdate("INSERT INTO public.user_roles (user_id, role_id) VALUES ('" + restrictedUserId + "', '" + testRoleId + "')");
        }

        shopOwnerToken = login("sec_owner@aquasuite.local", "Password123!");
        restrictedToken = login("restricted@aquasuite.local", "Password123!");
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanData();
    }

    private void cleanData() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM public.categories");
            stmt.executeUpdate("DELETE FROM public.brands");
            if (testRoleId != null) {
                stmt.executeUpdate("DELETE FROM public.role_permissions WHERE role_id = '" + testRoleId + "'");
            }
            stmt.executeUpdate("DELETE FROM public.user_roles WHERE user_id IN (SELECT id FROM public.users WHERE username IN ('sec_owner', 'restricted_user'))");
            if (testRoleId != null) {
                stmt.executeUpdate("DELETE FROM public.roles WHERE role_id = '" + testRoleId + "'");
            }
            stmt.executeUpdate("DELETE FROM public.users WHERE username IN ('sec_owner', 'restricted_user')");
        }
        Set<String> keys = redisTemplate.keys("auth:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String login(String email, String password) throws Exception {
        String reqBody = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("access_token").asText();
    }

    @Test
    @DisplayName("Security: SHOP_OWNER has all 8 catalog permissions across all 10 endpoints")
    void testShopOwnerHasAllCatalogPermissions() throws Exception {
        // 1. Can read categories list
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk());

        // 2. Can create category
        CreateCategoryRequest catReq = CreateCategoryRequest.builder()
                .code("CAT_SEC_1")
                .name("Sec Category")
                .build();
        MvcResult res = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(catReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String catId = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();

        // 3. Can get category detail
        mockMvc.perform(get("/api/v1/categories/{id}", catId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk());

        // 4. Can update category
        UpdateCategoryRequest catUpdate = UpdateCategoryRequest.builder()
                .name("Sec Category Updated")
                .status("ACTIVE")
                .build();
        mockMvc.perform(put("/api/v1/categories/{id}", catId)
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(catUpdate)))
                .andExpect(status().isOk());

        // 5. Can delete category
        mockMvc.perform(delete("/api/v1/categories/{id}", catId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk());

        // 6. Can read brands list
        mockMvc.perform(get("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk());

        // 7. Can create brand
        CreateBrandRequest brandReq = CreateBrandRequest.builder()
                .code("BRAND_SEC_1")
                .name("Sec Brand")
                .build();
        MvcResult brandRes = mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(brandReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String brandId = objectMapper.readTree(brandRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 8. Can get brand detail
        mockMvc.perform(get("/api/v1/brands/{id}", brandId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk());

        // 9. Can update brand
        UpdateBrandRequest brandUpdate = UpdateBrandRequest.builder()
                .name("Sec Brand Updated")
                .status("ACTIVE")
                .build();
        mockMvc.perform(put("/api/v1/brands/{id}", brandId)
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(brandUpdate)))
                .andExpect(status().isOk());

        // 10. Can delete brand
        mockMvc.perform(delete("/api/v1/brands/{id}", brandId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security: Unauthenticated request receives 401 Unauthorized across endpoints")
    void testUnauthenticatedRequestReceives401() throws Exception {
        UUID sampleId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/categories/{id}", sampleId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/categories/{id}", sampleId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/categories/{id}", sampleId)).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/brands")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/brands/{id}", sampleId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/brands")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/brands/{id}", sampleId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/brands/{id}", sampleId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Independent allowed and denied checks for all 8 catalog permissions")
    void testEightIndividualAuthoritiesAllowAndDeny() throws Exception {
        // Pre-create 1 category and 1 brand using SHOP_OWNER
        CreateCategoryRequest catReq = CreateCategoryRequest.builder().code("CAT_PERM_1").name("Perm Category").build();
        MvcResult catRes = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(catReq)))
                .andExpect(status().isCreated()).andReturn();
        String catId = objectMapper.readTree(catRes.getResponse().getContentAsString()).path("data").path("id").asText();

        CreateBrandRequest brandReq = CreateBrandRequest.builder().code("BRAND_PERM_1").name("Perm Brand").build();
        MvcResult brRes = mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(brandReq)))
                .andExpect(status().isCreated()).andReturn();
        String brandId = objectMapper.readTree(brRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // Initially restricted user has NO permissions -> All endpoints return 403 Forbidden
        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/categories/{id}", catId).header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/categories").header("Authorization", "Bearer " + restrictedToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(catReq))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/categories/{id}", catId).header("Authorization", "Bearer " + restrictedToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(UpdateCategoryRequest.builder().name("Up").build()))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/categories/{id}", catId).header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/brands").header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/brands/{id}", brandId).header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/brands").header("Authorization", "Bearer " + restrictedToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(brandReq))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/brands/{id}", brandId).header("Authorization", "Bearer " + restrictedToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(UpdateBrandRequest.builder().name("Up").build()))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/brands/{id}", brandId).header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isForbidden());

        // Now test each of the 8 permissions individually:
        UUID permCatRead = UUID.fromString("20000000-0000-4000-8000-000000000001");
        UUID permCatCreate = UUID.fromString("20000000-0000-4000-8000-000000000002");
        UUID permCatUpdate = UUID.fromString("20000000-0000-4000-8000-000000000003");
        UUID permCatDelete = UUID.fromString("20000000-0000-4000-8000-000000000004");
        UUID permBrandRead = UUID.fromString("20000000-0000-4000-8000-000000000005");
        UUID permBrandCreate = UUID.fromString("20000000-0000-4000-8000-000000000006");
        UUID permBrandUpdate = UUID.fromString("20000000-0000-4000-8000-000000000007");
        UUID permBrandDelete = UUID.fromString("20000000-0000-4000-8000-000000000008");

        // 1. Grant CATALOG_CATEGORY_READ only
        grantPermission(permCatRead);
        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categories/{id}", catId).header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isOk());
        revokePermission(permCatRead);

        // 2. Grant CATALOG_CATEGORY_CREATE only
        grantPermission(permCatCreate);
        CreateCategoryRequest newCat = CreateCategoryRequest.builder().code("CAT_NEW_CREATE").name("New Cat").build();
        mockMvc.perform(post("/api/v1/categories").header("Authorization", "Bearer " + restrictedToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(newCat))).andExpect(status().isCreated());
        revokePermission(permCatCreate);

        // 3. Grant CATALOG_CATEGORY_UPDATE only
        grantPermission(permCatUpdate);
        mockMvc.perform(put("/api/v1/categories/{id}", catId).header("Authorization", "Bearer " + restrictedToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(UpdateCategoryRequest.builder().name("Up Name").status("ACTIVE").build())))
                .andExpect(status().isOk());
        revokePermission(permCatUpdate);

        // 4. Grant CATALOG_CATEGORY_DELETE only
        grantPermission(permCatDelete);
        mockMvc.perform(delete("/api/v1/categories/{id}", catId).header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isOk());
        revokePermission(permCatDelete);

        // 5. Grant CATALOG_BRAND_READ only
        grantPermission(permBrandRead);
        mockMvc.perform(get("/api/v1/brands").header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/brands/{id}", brandId).header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isOk());
        revokePermission(permBrandRead);

        // 6. Grant CATALOG_BRAND_CREATE only
        grantPermission(permBrandCreate);
        CreateBrandRequest newBrand = CreateBrandRequest.builder().code("BRAND_NEW_CREATE").name("New Brand").build();
        mockMvc.perform(post("/api/v1/brands").header("Authorization", "Bearer " + restrictedToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(newBrand))).andExpect(status().isCreated());
        revokePermission(permBrandCreate);

        // 7. Grant CATALOG_BRAND_UPDATE only
        grantPermission(permBrandUpdate);
        mockMvc.perform(put("/api/v1/brands/{id}", brandId).header("Authorization", "Bearer " + restrictedToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(UpdateBrandRequest.builder().name("Up Brand").status("ACTIVE").build())))
                .andExpect(status().isOk());
        revokePermission(permBrandUpdate);

        // 8. Grant CATALOG_BRAND_DELETE only
        grantPermission(permBrandDelete);
        mockMvc.perform(delete("/api/v1/brands/{id}", brandId).header("Authorization", "Bearer " + restrictedToken)).andExpect(status().isOk());
        revokePermission(permBrandDelete);
    }

    private void grantPermission(UUID permissionId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('" +
                    testRoleId + "', '" + permissionId + "')");
        }
    }

    private void revokePermission(UUID permissionId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM public.role_permissions WHERE role_id = '" +
                    testRoleId + "' AND permission_id = '" + permissionId + "'");
        }
    }

    @Test
    @DisplayName("Security: Dynamic authority reload from DB applies on next request with SAME token without reissue")
    void testDynamicAuthorityReloadFromDatabase() throws Exception {
        // Initially, restricted user has NO permissions -> 403 Forbidden
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + restrictedToken))
                .andExpect(status().isForbidden());

        // Grant CATALOG_CATEGORY_READ in PostgreSQL
        grantPermission(catReadPermId);

        // Next request with the SAME token succeeds (200 OK)!
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + restrictedToken))
                .andExpect(status().isOk());

        // But still cannot create category (403 Forbidden)
        CreateCategoryRequest catReq = CreateCategoryRequest.builder()
                .code("CAT_UNAUTH")
                .name("Unauth Category")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + restrictedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(catReq)))
                .andExpect(status().isForbidden());

        // Grant CATALOG_CATEGORY_CREATE in PostgreSQL
        grantPermission(catCreatePermId);

        // Next request with the SAME token can create category (201 Created)!
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + restrictedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(catReq)))
                .andExpect(status().isCreated());

        // Revoke CATALOG_CATEGORY_READ from PostgreSQL
        revokePermission(catReadPermId);

        // Next request with the SAME token is 403 Forbidden again!
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + restrictedToken))
                .andExpect(status().isForbidden());
    }
}
