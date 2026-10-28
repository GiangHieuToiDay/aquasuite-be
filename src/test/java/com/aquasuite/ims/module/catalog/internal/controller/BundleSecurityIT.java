package com.aquasuite.ims.module.catalog.internal.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aquasuite.ims.AquasuiteBeApplication;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

/** Real JWT/session/current-authority coverage; no filter bypass or mock identity. */
@SpringBootTest(classes = AquasuiteBeApplication.class)
@AutoConfigureMockMvc
class BundleSecurityIT extends AuthIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;

    String loginAs(String role) throws Exception {
        UUID id = UUID.randomUUID();
        String email = id + "@bundle.test.invalid", password = UUID.randomUUID() + "Aa!1";
        jdbc.update(
                "insert into roles(role_id,role_name,is_active) values(?,?,true) on conflict do"
                        + " nothing",
                UUID.randomUUID(),
                role);
        UUID roleId =
                jdbc.queryForObject(
                        "select role_id from roles where role_name=?", UUID.class, role);
        jdbc.update(
                "insert into users(id,username,email,password_hash,status)"
                        + " values(?,?,?,?,'ACTIVE')",
                id,
                "bundle-" + id,
                email,
                passwords.encode(password));
        jdbc.update("insert into user_roles(user_id,role_id) values(?,?)", id, roleId);
        var response =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                email,
                                                                "password",
                                                                password))))
                        .andExpect(status().isOk())
                        .andReturn();
        return json.readTree(response.getResponse().getContentAsByteArray())
                .path("data")
                .path("access_token")
                .asText();
    }

    @Test
    void ownerCanManageButCustomerCanOnlyBrowse() throws Exception {
        getFaultProxy().disarm();
        String customer = loginAs("CUSTOMER"), owner = loginAs("SHOP_OWNER");
        mvc.perform(get("/api/v1/catalog/bundle/admin")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/catalog/bundle/admin")
                                .header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        mvc.perform(get("/api/v1/catalog/bundle").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/catalog/bundle/admin").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/catalog/bundle/admin/skus")
                                .header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch(
                                        "/api/v1/catalog/bundle/admin/"
                                                + UUID.randomUUID()
                                                + "/publication-status")
                                .header("Authorization", "Bearer " + customer)
                                .contentType("application/json")
                                .content(
                                        "{\"publicationStatus\":\"PUBLISHED\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
    }
}
