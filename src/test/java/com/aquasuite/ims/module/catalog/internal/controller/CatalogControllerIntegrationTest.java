package com.aquasuite.ims.module.catalog.internal.controller;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerIntegrationTest extends AuthIntegrationTestSupport {

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

    @BeforeEach
    void setUp() throws Exception {
        cleanData();

        UUID ownerId = UUID.randomUUID();
        String encodedPass = passwordEncoder.encode("Password123!");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO public.users (id, username, email, password_hash, status, created_at) " +
                    "VALUES ('" + ownerId + "', 'catalog_admin', 'catalog_admin@aquasuite.local', '" + encodedPass + "', 'ACTIVE', CURRENT_TIMESTAMP)");
            stmt.executeUpdate("INSERT INTO public.user_roles (user_id, role_id) " +
                    "SELECT '" + ownerId + "', role_id FROM public.roles WHERE role_name = 'SHOP_OWNER'");
        }

        shopOwnerToken = login("catalog_admin@aquasuite.local", "Password123!");
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
            stmt.executeUpdate("DELETE FROM public.user_roles WHERE user_id IN (SELECT id FROM public.users WHERE username = 'catalog_admin')");
            stmt.executeUpdate("DELETE FROM public.users WHERE username = 'catalog_admin'");
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
    @DisplayName("Category CRUD: full lifecycle, normalization, search, filter, pagination, sorting and tie-breaker")
    void testCategoryCrudLifecycle() throws Exception {
        // 1. Create root category with whitespace in code and name
        CreateCategoryRequest createRoot = CreateCategoryRequest.builder()
                .code(" cat_109 ")
                .name(" Pipes & Fittings ")
                .description("   Standard pipes   ")
                .status("ACTIVE")
                .build();

        MvcResult createRes = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRoot)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.code", is("SUCCESS")))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.code", is("CAT_109"))) // normalized
                .andExpect(jsonPath("$.data.name", is("Pipes & Fittings"))) // trimmed
                .andExpect(jsonPath("$.data.description", is("Standard pipes"))) // trimmed
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.parentId", nullValue()))
                .andExpect(jsonPath("$.data.createdAt", notNullValue()))
                .andExpect(jsonPath("$.data.updatedAt", notNullValue()))
                .andReturn();

        JsonNode rootJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        String rootId = rootJson.path("data").path("id").asText();

        // 2. Create child category under root
        CreateCategoryRequest createChild = CreateCategoryRequest.builder()
                .code(" 109 ")
                .name("PVC Pipes")
                .parentId(UUID.fromString(rootId))
                .build();

        MvcResult createChildRes = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createChild)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", is("109"))) // normalized
                .andExpect(jsonPath("$.data.name", is("PVC Pipes")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE"))) // default
                .andExpect(jsonPath("$.data.parentId", is(rootId)))
                .andReturn();

        String childId = objectMapper.readTree(createChildRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 3. Get detail
        mockMvc.perform(get("/api/v1/categories/{categoryId}", rootId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(rootId)))
                .andExpect(jsonPath("$.data.code", is("CAT_109")));

        // 4. Update root category (name, status, description)
        UpdateCategoryRequest updateReq = UpdateCategoryRequest.builder()
                .name("Pipes and Fittings Updated")
                .status("INACTIVE")
                .description("")
                .build();

        mockMvc.perform(put("/api/v1/categories/{categoryId}", rootId)
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Pipes and Fittings Updated")))
                .andExpect(jsonPath("$.data.status", is("INACTIVE")))
                .andExpect(jsonPath("$.data.description", nullValue())); // empty normalized to null

        // 5. Paginated list with search, status filter, sort, and ID tie-breaker
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", "pipes")
                        .param("sortBy", "name")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.items", hasSize(2)));

        // 6. Delete root blocked because it has child
        mockMvc.perform(delete("/api/v1/categories/{categoryId}", rootId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CAT_013")));

        // 7. Delete child succeeds
        mockMvc.perform(delete("/api/v1/categories/{categoryId}", childId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data").doesNotExist()); // null data omitted

        // 8. Now delete root succeeds
        mockMvc.perform(delete("/api/v1/categories/{categoryId}", rootId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("Category validation: negative cases, duplicate codes, duplicate names, invalid status")
    void testCategoryValidationErrors() throws Exception {
        // Invalid code format: CAT 109 with spaces inside
        CreateCategoryRequest invalidCode1 = CreateCategoryRequest.builder()
                .code("CAT 109")
                .name("Cat Name")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCode1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CAT_003")));

        // Invalid code format: special chars CAT-109
        CreateCategoryRequest invalidCode2 = CreateCategoryRequest.builder()
                .code("CAT-109")
                .name("Cat Name")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCode2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CAT_003")));

        // Invalid code format: leading underscore
        CreateCategoryRequest invalidCode3 = CreateCategoryRequest.builder()
                .code("_CAT_109")
                .name("Cat Name")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCode3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CAT_003")));

        // Description > 5000 characters
        CreateCategoryRequest tooLongDesc = CreateCategoryRequest.builder()
                .code("CAT_LONG")
                .name("Cat Name")
                .description("a".repeat(5001))
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooLongDesc)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_003")));

        // Invalid status in create
        CreateCategoryRequest invalidStatus = CreateCategoryRequest.builder()
                .code("CAT_STATUS")
                .name("Cat Name")
                .status("PENDING")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidStatus)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CAT_014")));

        // Create valid root category
        CreateCategoryRequest validRoot = CreateCategoryRequest.builder()
                .code("CAT_UNIQUE")
                .name("Root Category Name")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRoot)))
                .andExpect(status().isCreated());

        // Duplicate code (case-insensitive) -> CAT_005
        CreateCategoryRequest dupCode = CreateCategoryRequest.builder()
                .code("cat_unique")
                .name("Other Name")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CAT_005")));

        // Duplicate root name (case-insensitive) -> CAT_007
        CreateCategoryRequest dupName = CreateCategoryRequest.builder()
                .code("CAT_OTHER")
                .name("root category name")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupName)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CAT_007")));

        // Invalid query params in GET: page < 0 -> SYS_002
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_002")));

        // Invalid query params: size > 100 -> SYS_002
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_002")));

        // Invalid query params: sortBy -> SYS_002
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .param("sortBy", "invalidField"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_002")));

        // Invalid query params: direction -> SYS_002
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .param("direction", "upwards"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_002")));

        // Invalid query params: status -> SYS_002
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .param("status", "ARCHIVED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_002")));
    }

    @Test
    @DisplayName("Brand CRUD: full lifecycle, normalization, search, filter, pagination and duplicate checks")
    void testBrandCrudLifecycle() throws Exception {
        // 1. Create brand with lowercase code and surrounding whitespace
        CreateBrandRequest createReq = CreateBrandRequest.builder()
                .code(" toto ")
                .name(" Toto Sanitary ")
                .description("   Japan brand   ")
                .status("ACTIVE")
                .build();

        MvcResult createRes = mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.code", is("TOTO"))) // uppercase Locale.ROOT
                .andExpect(jsonPath("$.data.name", is("Toto Sanitary")))
                .andExpect(jsonPath("$.data.description", is("Japan brand")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.createdAt").doesNotExist()) // Brand has NO timestamps
                .andReturn();

        String brandId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 2. Duplicate brand code -> CAT_006
        CreateBrandRequest dupCode = CreateBrandRequest.builder()
                .code("TOTO")
                .name("Other Name")
                .build();
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CAT_006")));

        // 3. Duplicate brand name (global case-insensitive) -> CAT_008
        CreateBrandRequest dupName = CreateBrandRequest.builder()
                .code("BRAND_X")
                .name("toto sanitary")
                .build();
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupName)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CAT_008")));

        // 4. Get brand detail
        mockMvc.perform(get("/api/v1/brands/{brandId}", brandId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code", is("TOTO")));

        // 5. Update brand
        UpdateBrandRequest updateReq = UpdateBrandRequest.builder()
                .name("Toto Corporation")
                .status("INACTIVE")
                .description("Updated description")
                .build();
        mockMvc.perform(put("/api/v1/brands/{brandId}", brandId)
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Toto Corporation")))
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));

        // 6. List brands
        mockMvc.perform(get("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .param("search", "toto")
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(1)))
                .andExpect(jsonPath("$.data.items[0].code", is("TOTO")));

        // 7. Delete brand
        mockMvc.perform(delete("/api/v1/brands/{brandId}", brandId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        // 8. Get deleted brand -> 404 CAT_002
        mockMvc.perform(get("/api/v1/brands/{brandId}", brandId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CAT_002")));
    }

    @Test
    @DisplayName("Validation: Request body Bean Validation errors produce SYS_003 with structured field errors")
    void testCategoryAndBrandBeanValidationFieldErrors() throws Exception {
        // 1. Category create with null/blank code and name
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\", \"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_003")))
                .andExpect(jsonPath("$.errors", notNullValue()));

        // 2. Category create with name > 100 characters
        CreateCategoryRequest catNameTooLong = CreateCategoryRequest.builder()
                .code("CAT_VALID")
                .name("a".repeat(101))
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(catNameTooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_003")))
                .andExpect(jsonPath("$.errors", notNullValue()));

        // 3. Category create with description > 5000 characters
        CreateCategoryRequest catDescTooLong = CreateCategoryRequest.builder()
                .code("CAT_VALID")
                .name("Valid Name")
                .description("d".repeat(5001))
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(catDescTooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_003")));

        // 4. Category update with blank name
        UUID sampleId = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/categories/{id}", sampleId)
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_003")));

        // 5. Brand create with null/blank code and name
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\", \"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_003")));

        // 6. Brand create with name > 100 characters
        CreateBrandRequest brandNameTooLong = CreateBrandRequest.builder()
                .code("BRAND_VALID")
                .name("b".repeat(101))
                .build();
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(brandNameTooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_003")));

        // 7. Brand create with description > 5000 characters
        CreateBrandRequest brandDescTooLong = CreateBrandRequest.builder()
                .code("BRAND_VALID")
                .name("Valid Name")
                .description("d".repeat(5001))
                .build();
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(brandDescTooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_003")));

        // 8. Brand update with blank name
        mockMvc.perform(put("/api/v1/brands/{id}", sampleId)
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_003")));
    }

    @Test
    @DisplayName("Normalization: Unicode accent, length boundaries (1, 50, 51, 100, 5000) and canonical persistence")
    void testCodeNormalizationAndLengthBoundaries() throws Exception {
        // 1. Unicode accented code rejected (CAT_003 and CAT_004)
        CreateCategoryRequest unicodeCat = CreateCategoryRequest.builder()
                .code("CÁT_109")
                .name("Unicode Category")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unicodeCat)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CAT_003")));

        CreateBrandRequest unicodeBrand = CreateBrandRequest.builder()
                .code("BRÁND_109")
                .name("Unicode Brand")
                .build();
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unicodeBrand)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CAT_004")));

        // 2. Code length 1 succeeds
        CreateCategoryRequest len1Cat = CreateCategoryRequest.builder()
                .code("A")
                .name("Category A")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(len1Cat)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", is("A")));

        CreateBrandRequest len1Brand = CreateBrandRequest.builder()
                .code("1")
                .name("Brand 1")
                .build();
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(len1Brand)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", is("1")));

        // 3. Code length 50 succeeds
        String code50 = "A".repeat(50);
        CreateCategoryRequest len50Cat = CreateCategoryRequest.builder()
                .code(" " + code50.toLowerCase() + " ")
                .name("Category Len 50")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(len50Cat)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", is(code50)));

        String brandCode50 = "B".repeat(50);
        CreateBrandRequest len50Brand = CreateBrandRequest.builder()
                .code(" " + brandCode50.toLowerCase() + " ")
                .name("Brand Len 50")
                .build();
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(len50Brand)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", is(brandCode50)));

        // 4. Code length 51 rejected (CAT_003 and CAT_004)
        String code51 = "A".repeat(51);
        CreateCategoryRequest len51Cat = CreateCategoryRequest.builder()
                .code(code51)
                .name("Category Len 51")
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(len51Cat)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CAT_003")));

        String brandCode51 = "B".repeat(51);
        CreateBrandRequest len51Brand = CreateBrandRequest.builder()
                .code(brandCode51)
                .name("Brand Len 51")
                .build();
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(len51Brand)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CAT_004")));

        // 5. Name length 100 succeeds
        String name100 = "N".repeat(100);
        CreateCategoryRequest name100Cat = CreateCategoryRequest.builder()
                .code("CAT_NAME_100")
                .name(name100)
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(name100Cat)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name", is(name100)));

        CreateBrandRequest name100Brand = CreateBrandRequest.builder()
                .code("BRAND_NAME_100")
                .name(name100)
                .build();
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(name100Brand)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name", is(name100)));

        // 6. Description length 5000 succeeds
        String desc5000 = "D".repeat(5000);
        CreateCategoryRequest desc5000Cat = CreateCategoryRequest.builder()
                .code("CAT_DESC_5000")
                .name("Category Desc 5000")
                .description(desc5000)
                .build();
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(desc5000Cat)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.description", is(desc5000)));

        CreateBrandRequest desc5000Brand = CreateBrandRequest.builder()
                .code("BRAND_DESC_5000")
                .name("Brand Desc 5000")
                .description(desc5000)
                .build();
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(desc5000Brand)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.description", is(desc5000)));
    }

    @Test
    @DisplayName("Contract: Code is immutable on update request and unknown JSON fields are ignored")
    void testImmutableCodeInUpdateAndUnknownJsonFields() throws Exception {
        // 1. Create Category with extra unknown JSON fields
        String createCatJson = "{" +
                "\"code\": \"CAT_IMMUTABLE\", " +
                "\"name\": \"Initial Name\", " +
                "\"unknownField\": \"extraValue\", " +
                "\"anotherUnknownNumber\": 12345" +
                "}";
        MvcResult catRes = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCatJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", is("CAT_IMMUTABLE")))
                .andExpect(jsonPath("$.data.name", is("Initial Name")))
                .andReturn();

        String catId = objectMapper.readTree(catRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 2. Update Category providing a new code in payload -> code remains immutable/ignored
        String updateCatJson = "{" +
                "\"code\": \"ATTEMPTED_NEW_CODE\", " +
                "\"name\": \"Updated Name\", " +
                "\"status\": \"ACTIVE\", " +
                "\"unknownFlag\": true" +
                "}";
        mockMvc.perform(put("/api/v1/categories/{id}", catId)
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateCatJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code", is("CAT_IMMUTABLE")))
                .andExpect(jsonPath("$.data.name", is("Updated Name")));

        // Verify with GET detail
        mockMvc.perform(get("/api/v1/categories/{id}", catId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code", is("CAT_IMMUTABLE")))
                .andExpect(jsonPath("$.data.name", is("Updated Name")));

        // 3. Create Brand with extra unknown JSON fields
        String createBrandJson = "{" +
                "\"code\": \"BRAND_IMMUTABLE\", " +
                "\"name\": \"Initial Brand Name\", " +
                "\"extraCustomAttribute\": false" +
                "}";
        MvcResult brandRes = mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBrandJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", is("BRAND_IMMUTABLE")))
                .andReturn();

        String brandId = objectMapper.readTree(brandRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 4. Update Brand providing code in payload -> code remains immutable/ignored
        String updateBrandJson = "{" +
                "\"code\": \"BRAND_NEW_CODE\", " +
                "\"name\": \"Updated Brand Name\", " +
                "\"status\": \"ACTIVE\", " +
                "\"randomMetadata\": {\"subKey\": 999}" +
                "}";
        mockMvc.perform(put("/api/v1/brands/{id}", brandId)
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBrandJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code", is("BRAND_IMMUTABLE")))
                .andExpect(jsonPath("$.data.name", is("Updated Brand Name")));

        // Verify with GET detail
        mockMvc.perform(get("/api/v1/brands/{id}", brandId)
                        .header("Authorization", "Bearer " + shopOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code", is("BRAND_IMMUTABLE")))
                .andExpect(jsonPath("$.data.name", is("Updated Brand Name")));
    }

    @Test
    @DisplayName("Brand list query: Invalid status filter returns 400 SYS_002")
    void testInvalidStatusFilterOnBrandList() throws Exception {
        mockMvc.perform(get("/api/v1/brands")
                        .header("Authorization", "Bearer " + shopOwnerToken)
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SYS_002")));
    }
}
