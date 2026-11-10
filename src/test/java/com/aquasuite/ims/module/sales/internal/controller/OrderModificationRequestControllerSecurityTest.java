package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.internal.security.AccessAuthenticationConverter;
import com.aquasuite.ims.module.iam.internal.security.AuthSecurityConfig;
import com.aquasuite.ims.module.sales.internal.payload.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderModificationRequestResponse;
import com.aquasuite.ims.module.sales.internal.service.OrderModificationRequestService;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderModificationRequestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {AuthSecurityConfig.class, AccessAuthenticationConverter.class}))
@Import({GlobalExceptionHandler.class, OrderModificationRequestControllerSecurityTest.TestSecurityConfig.class})
class OrderModificationRequestControllerSecurityTest {
    private static final UUID REQUEST_ID = UUID.fromString("10000000-0000-4000-8000-000000000026");

    @Autowired private MockMvc mockMvc;
    @MockBean private OrderModificationRequestService service;
    @MockBean private IamApi iamApi;

    @Test
    void unauthenticatedUserCannotReadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/sales/order-modification-requests/{requestId}", REQUEST_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanReadRequest() throws Exception {
        when(service.detail(REQUEST_ID)).thenReturn(OrderModificationRequestResponse.builder().id(REQUEST_ID).build());
        mockMvc.perform(get("/api/v1/sales/order-modification-requests/{requestId}", REQUEST_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(REQUEST_ID.toString()));
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCannotRejectRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sales/order-modification-requests/{requestId}/reject", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decisionNote\":\"Rejected\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffRejectUsesCurrentActor() throws Exception {
        UUID actorId = UUID.randomUUID();
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(service.reject(eq(REQUEST_ID), any(ReviewOrderModificationRequest.class), eq(actorId)))
                .thenReturn(OrderModificationRequestResponse.builder().id(REQUEST_ID).build());
        mockMvc.perform(post("/api/v1/sales/order-modification-requests/{requestId}/reject", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decisionNote\":\"Rejected\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(service).reject(eq(REQUEST_ID), any(ReviewOrderModificationRequest.class), eq(actorId));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .httpBasic(httpBasic -> { })
                    .build();
        }
    }
}
