package com.aquasuite.ims.controller.iam;

import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.controller.iam.UserAccountController;
import com.aquasuite.ims.dto.iam.response.AccountPageResponse;
import com.aquasuite.ims.dto.iam.response.AccountStatsResponse;
import com.aquasuite.ims.dto.iam.response.UserAccountResponse;
import com.aquasuite.ims.service.iam.UserAccountService;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(UserAccountControllerSecurityTest.Config.class)
class UserAccountControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class Config implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new PageableHandlerMethodArgumentResolver());
        }

        @Bean
        UserAccountService accounts() {
            return mock(UserAccountService.class);
        }

        @Bean
        IamQueryService iam() {
            return mock(IamQueryService.class);
        }

        @Bean
        UserAccountController controller(UserAccountService service, IamQueryService iam) {
            return new UserAccountController(service, iam);
        }

        @Bean
        GlobalExceptionHandler errors() {
            return new GlobalExceptionHandler();
        }

        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable())
                    .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    UserAccountService accounts;

    @Autowired
    IamQueryService iam;

    MockMvc mvc;

    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID ROLE_ID = UUID.fromString("10000000-0000-4000-8000-000000000003");

    @BeforeEach
    void setup() {
        reset(accounts, iam);
        when(iam.getCurrentUserId()).thenReturn(ACTOR_ID);
        when(accounts.stats()).thenReturn(AccountStatsResponse.builder()
                .total(5).active(3).inactive(1).locked(1).build());
        when(accounts.search(any(), any(), any(), any(), any())).thenReturn(AccountPageResponse.builder()
                .content(List.of(UserAccountResponse.builder().id(TARGET_ID).username("staff.one").status("ACTIVE").build()))
                .page(0).size(20).totalElements(1).totalPages(1).build());
        when(accounts.detail(any(), any())).thenReturn(UserAccountResponse.builder()
                .id(TARGET_ID).username("staff.one").status("ACTIVE").build());
        when(accounts.create(any(), any())).thenReturn(UserAccountResponse.builder()
                .id(TARGET_ID).username("staff.one").status("ACTIVE").build());
        when(accounts.update(any(), any(), any())).thenReturn(UserAccountResponse.builder()
                .id(TARGET_ID).username("staff.updated").status("ACTIVE").build());
        when(accounts.changeStatus(any(), any(), any())).thenReturn(UserAccountResponse.builder()
                .id(TARGET_ID).username("staff.one").status("LOCKED").build());

        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanAccessAllAccountEndpoints() throws Exception {
        mvc.perform(get("/api/v1/iam/users/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5));

        mvc.perform(get("/api/v1/iam/users").param("keyword", "staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].username").value("staff.one"));

        mvc.perform(get("/api/v1/iam/users/{id}", TARGET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(TARGET_ID.toString()));

        String createJson = """
                {
                  "username": "staff.one",
                  "email": "staff.one@example.com",
                  "phone": "0901234567",
                  "roleIds": ["%s"],
                  "autoGenerateTemporaryPassword": true
                }
                """.formatted(ROLE_ID);
        mvc.perform(post("/api/v1/iam/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("staff.one"));

        String updateJson = """
                {
                  "username": "staff.updated",
                  "email": "staff.updated@example.com",
                  "phone": "0901234567",
                  "roleIds": ["%s"]
                }
                """.formatted(ROLE_ID);
        mvc.perform(patch("/api/v1/iam/users/{id}", TARGET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("staff.updated"));

        mvc.perform(patch("/api/v1/iam/users/{id}/status", TARGET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"LOCKED\",\"reason\":\"Policy violation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOCKED"));

        verify(accounts).stats();
        verify(accounts).search(any(), any(), any(), any(), any());
        verify(accounts).detail(any(), any());
        verify(accounts).create(any(), any());
        verify(accounts).update(any(), any(), any());
        verify(accounts).changeStatus(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffIsForbiddenFromAllAccountManagementEndpoints() throws Exception {
        assertAllEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantIsForbiddenFromAllAccountManagementEndpoints() throws Exception {
        assertAllEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerIsForbiddenFromAllAccountManagementEndpoints() throws Exception {
        assertAllEndpointsForbidden();
    }

    @Test
    void anonymousUserIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/iam/users/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/iam/users")).andExpect(status().isUnauthorized());
        verifyNoInteractions(accounts);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void createRejectsInvalidPayload() throws Exception {
        String invalidJson = """
                {
                  "username": " ",
                  "email": "not-an-email",
                  "phone": "+84901234567",
                  "roleIds": []
                }
                """;
        mvc.perform(post("/api/v1/iam/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        verifyNoInteractions(accounts);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void updateRejectsInvalidPayload() throws Exception {
        String invalidJson = """
                {
                  "username": "",
                  "email": "invalid",
                  "phone": "123",
                  "roleIds": []
                }
                """;
        mvc.perform(patch("/api/v1/iam/users/{id}", TARGET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        verifyNoInteractions(accounts);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void changeStatusRejectsBlankTargetStatus() throws Exception {
        mvc.perform(patch("/api/v1/iam/users/{id}/status", TARGET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        verifyNoInteractions(accounts);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void searchRejectsInvalidSortByWithSys002() throws Exception {
        mvc.perform(get("/api/v1/iam/users").param("sortBy", "passwordHash"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));

        verifyNoInteractions(accounts);
    }

    private void assertAllEndpointsForbidden() throws Exception {
        String validPayload = """
                {
                  "username": "staff.one",
                  "email": "staff.one@example.com",
                  "phone": "0901234567",
                  "roleIds": ["%s"],
                  "autoGenerateTemporaryPassword": true
                }
                """.formatted(ROLE_ID);

        mvc.perform(get("/api/v1/iam/users/stats")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/iam/users")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/iam/users/{id}", TARGET_ID)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/iam/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/iam/users/{id}", TARGET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/iam/users/{id}/status", TARGET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"LOCKED\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accounts);
    }
}
