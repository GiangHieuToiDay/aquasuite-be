package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.internal.service.UserAccountService;
import com.aquasuite.ims.module.iam.internal.payload.response.*;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(ShipperLookupSecurityTest.Config.class)
class ShipperLookupSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
        @Override public void addArgumentResolvers(java.util.List<org.springframework.web.method.support.HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new org.springframework.data.web.PageableHandlerMethodArgumentResolver());
        }
        @Bean UserAccountService accounts() { return mock(UserAccountService.class); }
        @Bean IamApi iam() { return mock(IamApi.class); }
        @Bean UserAccountController controller(UserAccountService service,IamApi iam) { return new UserAccountController(service,iam); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .httpBasic(org.springframework.security.config.Customizer.withDefaults()).build();
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired UserAccountService accounts;
    MockMvc mvc;
    @BeforeEach void setup() {
        reset(accounts);
        when(accounts.search(any(),any(),any(),any(),any())).thenReturn(AccountPageResponse.builder()
                .content(List.of(UserAccountResponse.builder().id(UUID.randomUUID()).username("shipper").build()))
                .page(0).size(20).totalElements(1).totalPages(1).build());
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test @WithMockUser(roles="ACCOUNTANT") void accountantCanReadActiveShippersOnly() throws Exception {
        mvc.perform(get("/api/v1/iam/users/shippers?keyword=abc&roleId=other&status=LOCKED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].username").value("shipper"));
        verify(accounts).search(eq("abc"),eq(UUID.fromString("10000000-0000-4000-8000-000000000005")),eq("ACTIVE"),any(),any());
    }
    @Test @WithMockUser(roles="SHOP_OWNER") void ownerCanRead() throws Exception {
        mvc.perform(get("/api/v1/iam/users/shippers")).andExpect(status().isOk());
    }
    @Test @WithMockUser(roles="SHIPPER") void shipperCannotListStaff() throws Exception {
        mvc.perform(get("/api/v1/iam/users/shippers")).andExpect(status().isForbidden()); verifyNoInteractions(accounts);
    }
    @Test @WithMockUser(roles="SALES_STAFF") void salesCannotListStaff() throws Exception {
        mvc.perform(get("/api/v1/iam/users/shippers")).andExpect(status().isForbidden());
    }
    @Test void anonymousCannotRead() throws Exception {
        mvc.perform(get("/api/v1/iam/users/shippers")).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser(roles="ACCOUNTANT") void rejectsOversizedPage() throws Exception {
        mvc.perform(get("/api/v1/iam/users/shippers?size=101")).andExpect(status().isBadRequest()); verifyNoInteractions(accounts);
    }
    @Test @WithMockUser(roles="ACCOUNTANT") void accountantCannotReadOtherAccounts() throws Exception {
        mvc.perform(get("/api/v1/iam/users")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/iam/users/"+UUID.randomUUID())).andExpect(status().isForbidden());
    }
}
