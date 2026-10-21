package com.aquasuite.ims.integration;

import com.aquasuite.ims.AquasuiteBeApplication;
import com.aquasuite.ims.module.catalog.internal.payload.bundle.request.*;
import com.aquasuite.ims.module.catalog.internal.payload.bundle.response.*;
import com.aquasuite.ims.module.catalog.internal.service.BundleAdminService;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Complete application with real login/JWT/Redis and catalog/inventory providers. */
@SpringBootTest(classes = AquasuiteBeApplication.class)
@AutoConfigureMockMvc
class AuthenticationMergeIT extends AuthIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired BundleAdminService bundles;
    final UUID userId = UUID.randomUUID(),
            category = UUID.randomUUID(),
            product = UUID.randomUUID(),
            sku = UUID.randomUUID();
    final String password = UUID.randomUUID() + "Aa!1";
    BundleResponse bundle;

    @BeforeEach
    void fixture() {
        getFaultProxy().disarm();
        jdbc.execute(
                "TRUNCATE user_roles,role_permissions,user_password_recovery,users,roles,permissions,bundle_items,bundle_configurations,product_images,product_variants,products,categories,brands,inventories,stock_movements,audit_entries CASCADE");
        users.save(
                User.builder()
                        .id(userId)
                        .username("merge-user")
                        .email("merge@test.invalid")
                        .passwordHash(passwords.encode(password))
                        .status(UserStatus.ACTIVE)
                        .build());
        jdbc.update(
                "INSERT INTO categories(category_id,category_code,category_name,status) VALUES(?,'TEST','Test','ACTIVE')",
                category);
        jdbc.update(
                "INSERT INTO products(product_id,product_code,product_name,category_id,product_type,status) VALUES(?,'NORMAL','Normal',?,'NORMAL','ACTIVE')",
                product,
                category);
        jdbc.update(
                "INSERT INTO product_variants(variant_id,product_id,sku,variant_name,unit,conversion_rate,weight,cost_price,selling_price,serial_tracking,status) VALUES(?,?,'SKU','Variant','piece',1,0,0,110,false,'ACTIVE')",
                sku,
                product);
        jdbc.update(
                "INSERT INTO inventories(inventory_id,variant_id,warehouse_code,on_hand_qty,reserved_qty,minimum_qty,updated_at) VALUES(?,?,'WH_MAIN',5,0,0,CURRENT_TIMESTAMP)",
                UUID.randomUUID(),
                sku);
        var now = Instant.now();
        bundle =
                bundles.create(
                        new BundleCoreCreateRequest(
                                "MERGE",
                                "Combo",
                                category,
                                null,
                                null,
                                List.of(new BundleItemRequest(sku, 1)),
                                new BigDecimal("95"),
                                now.minusSeconds(60),
                                now.plusSeconds(3600),
                                List.of()));
        bundle = bundles.publish(bundle.bundleId(), new BundleVersionRequest(bundle.version()));
    }

    JsonNode login() throws Exception {
        var result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                "merge@test.invalid",
                                                                "password",
                                                                password))))
                        .andExpect(status().isOk())
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }

    @Test
    void allNonAuthApiFamiliesRequireJwt() throws Exception {
        for (String path :
                List.of(
                        "/api/v1/catalog/bundles",
                        "/api/v1/catalog/admin/bundles",
                        "/api/v1/catalog/admin/bundle-skus",
                        "/api/v1/inventory/" + sku)) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_001"));
        }
        mvc.perform(
                        post("/api/v1/iam/password/change")
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/catalog/bundles/" + bundle.bundleId() + "/evaluations")
                                .contentType("application/json")
                                .content("{\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginAccessTokenReadsAndEvaluatesComboWithRealInventory() throws Exception {
        String access = login().get("access_token").asText();
        mvc.perform(get("/api/v1/catalog/bundles").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(
                        get("/api/v1/catalog/admin/bundles")
                                .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/catalog/bundles/" + bundle.bundleId() + "/evaluations")
                                .header("Authorization", "Bearer " + access)
                                .contentType("application/json")
                                .content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPackagePrice").value(190))
                .andExpect(jsonPath("$.data.purchasable").value(true));
    }

    @Test
    void malformedAndRefreshTokensCannotAccessCatalog() throws Exception {
        mvc.perform(get("/api/v1/catalog/bundles").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
        String refresh = login().get("refresh_token").asText();
        mvc.perform(get("/api/v1/catalog/bundles").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesAccessToCatalogAndInventory() throws Exception {
        String access = login().get("access_token").asText();
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/catalog/bundles").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/inventory/" + sku).header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inventoryMutationUsesJwtUuidActor() throws Exception {
        String access = login().get("access_token").asText();
        mvc.perform(
                        post("/api/v1/inventory/adjust")
                                .header("Authorization", "Bearer " + access)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "variantId",
                                                        sku,
                                                        "deltaQty",
                                                        2,
                                                        "reason",
                                                        "test"))))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT created_by FROM stock_movements", UUID.class))
                .isEqualTo(userId);
    }

    @Test
    void passwordChangeUsesSameAuthenticatedUuidAccount() throws Exception {
        String access = login().get("access_token").asText();
        String next = UUID.randomUUID() + "Bb!2";
        mvc.perform(
                        post("/api/v1/iam/password/change")
                                .header("Authorization", "Bearer " + access)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "currentPassword",
                                                        password,
                                                        "newPassword",
                                                        next,
                                                        "confirmNewPassword",
                                                        next))))
                .andExpect(status().isOk());
        assertThat(passwords.matches(next, users.findById(userId).orElseThrow().getPasswordHash()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT user_id FROM user_password_recovery", UUID.class))
                .isEqualTo(userId);
    }

    @Test
    void disabledUserCannotReusePreviouslyIssuedAccessToken() throws Exception {
        String access = login().get("access_token").asText();
        jdbc.update("UPDATE users SET status='INACTIVE' WHERE id=?", userId);
        mvc.perform(get("/api/v1/catalog/bundles").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
    }
}
