package com.aquasuite.ims.module.iam.internal.auth;

import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(value = "prod", inheritProfiles = false)
class AuthProductionSecurityTest extends AuthIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("W-04 & AC-11: Xác minh cấu hình production độc lập và vô hiệu hóa các thuộc tính Springdoc/Swagger")
    void shouldVerifyProductionConfigurationAndDisabledSwaggerProperties() {
        // Assert only prod profile is active, not mixed with test profile
        assertThat(environment.getActiveProfiles()).containsExactly("prod");
        assertThat(environment.getProperty("aquasuite.config.environment")).isEqualTo("prod");

        // Assert explicit production properties disabling Swagger UI and OpenAPI docs
        assertThat(environment.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("AC-11: Swagger và OpenAPI documentation không bao giờ public, đều bị chặn bởi SecurityFilterChain yêu cầu 401 AUTH_001")
    void shouldDenyUnauthenticatedAccessToSwaggerAndApiDocs() throws Exception {
        // Even if Springdoc is present on classpath or activated, SecurityFilterChain restricts access
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.UNAUTHORIZED.getMessage()));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.UNAUTHORIZED.getMessage()));

        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("Login, refresh and the three registration POST routes are public; other routes remain protected")
    void shouldEnforceExactPublicMethodPathMatchers() throws Exception {
        for (String path : java.util.List.of("/api/v1/customer-registrations",
                "/api/v1/customer-registrations/verifications", "/api/v1/customer-registrations/otp-resends")) {
            mockMvc.perform(post(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        // GET /api/v1/auth/login is not permitted
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()));

        // POST /api/v1/auth/logout requires authentication
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()));

        // Random non-public path
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()));
    }
}
