package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.enums.CustomerCreditProfileRequestStatus;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileRequestResponse;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.service.CustomerCreditProfileRequestService;
import com.aquasuite.ims.module.crm.internal.service.CustomerCreditProfileService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.internal.security.AccessAuthenticationConverter;
import com.aquasuite.ims.module.iam.internal.security.AuthSecurityConfig;
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
import org.springframework.data.domain.Page;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {CustomerController.class, CustomerCreditProfileRequestController.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {AuthSecurityConfig.class, AccessAuthenticationConverter.class}))
@Import({GlobalExceptionHandler.class, CustomerCreditProfileRequestControllerSecurityTest.TestSecurityConfig.class})
class CustomerCreditProfileRequestControllerSecurityTest {
    private static final UUID CUSTOMER_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("10000000-0000-4000-8000-000000000002");

    @Autowired private MockMvc mockMvc;
    @MockBean private CustomerRepository customerRepository;
    @MockBean private CustomerCreditProfileService customerCreditProfileService;
    @MockBean private CustomerCreditProfileRequestService customerCreditProfileRequestService;
    @MockBean private IamApi iamApi;

    @Test
    void unauthenticatedApprovalQueueIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/crm/credit-profile-requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffMaySubmitRequestButCannotReadApprovalQueueOrUseLegacyBypass() throws Exception {
        when(customerCreditProfileRequestService.submit(eq(CUSTOMER_ID), any())).thenReturn(response(false, CustomerCreditProfileRequestStatus.PENDING));

        mockMvc.perform(post("/api/v1/crm/customers/{customerId}/credit-profile-requests", CUSTOMER_ID)
                        .contentType("application/json")
                        .content("{\"creditLimit\":2000000,\"creditTermDays\":15,\"reason\":\"Khách cần mua chịu\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/api/v1/crm/credit-profile-requests"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/crm/customers/{customerId}/credit-profile", CUSTOMER_ID)
                        .contentType("application/json")
                        .content("{\"creditLimit\":2000000,\"creditTermDays\":15,\"approvalToken\":\"legacy\",\"reason\":\"legacy\"}"))
                .andExpect(status().isForbidden());

        verify(customerCreditProfileRequestService).submit(eq(CUSTOMER_ID), any());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanReadPendingQueue() throws Exception {
        when(customerCreditProfileRequestService.list(eq(CustomerCreditProfileRequestStatus.PENDING), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/crm/credit-profile-requests?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private CustomerCreditProfileRequestResponse response(boolean appliedImmediately, CustomerCreditProfileRequestStatus status) {
        return CustomerCreditProfileRequestResponse.builder()
                .requestId(REQUEST_ID)
                .customerId(CUSTOMER_ID)
                .requestedCreditLimit(new BigDecimal("2000000"))
                .requestedCreditTermDays(15)
                .status(status)
                .appliedImmediately(appliedImmediately)
                .qualifiedOrderCount(0)
                .identityEvidenceProvided(false)
                .build();
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
