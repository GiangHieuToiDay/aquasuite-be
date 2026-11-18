package com.aquasuite.ims.module.servicedesk.internal.controller;

import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyLookupResponse;
import com.aquasuite.ims.module.servicedesk.internal.service.WarrantyLookupService;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WarrantyLookupController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.module\\.iam\\.internal\\.security\\..*"))
@Import({GlobalExceptionHandler.class, WarrantyLookupControllerSecurityTest.TestSecurityConfig.class})
class WarrantyLookupControllerSecurityTest {

    private static final UUID RECORD_ID = UUID.fromString("30000000-0000-4000-8000-000000000048");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarrantyLookupService warrantyLookupService;

    @Test
    void unauthenticatedLookupIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/customer/warranties"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(warrantyLookupService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCanLookupOwnWarrantiesByKeyword() throws Exception {
        WarrantyLookupResponse item = WarrantyLookupResponse.builder()
                .warrantyRecordId(RECORD_ID)
                .productName("Bồn cầu TOTO")
                .variantName("CS886")
                .sku("CS886")
                .serialNumber("SN-CS886-260101")
                .orderCode("ORD-202611-001")
                .warrantyTerms("Bảo hành 10 năm phần sứ.")
                .startDate(LocalDate.of(2026, 11, 13))
                .endDate(LocalDate.of(2036, 11, 13))
                .remainingDays(3650L)
                .status("ACTIVE")
                .claimAllowed(true)
                .build();
        when(warrantyLookupService.findMine("SN-CS886")).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/customer/warranties").param("keyword", "SN-CS886"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].serialNumber").value("SN-CS886-260101"))
                .andExpect(jsonPath("$.data[0].orderCode").value("ORD-202611-001"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].claimAllowed").value(true));

        verify(warrantyLookupService).findMine("SN-CS886");
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCannotCallCustomerLookupEndpoint() throws Exception {
        assertForbidden();
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCannotCallCustomerLookupEndpoint() throws Exception {
        assertForbidden();
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCannotCallCustomerLookupEndpoint() throws Exception {
        assertForbidden();
    }

    @Test
    @WithMockUser(roles = "TECHNICAL_STAFF")
    void technicalStaffCannotCallCustomerLookupEndpoint() throws Exception {
        assertForbidden();
    }

    @Test
    @WithMockUser(roles = "SHIPPER")
    void shipperCannotCallCustomerLookupEndpoint() throws Exception {
        assertForbidden();
    }

    private void assertForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/customer/warranties"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        verifyNoInteractions(warrantyLookupService);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .httpBasic(httpBasic -> { })
                    .build();
        }
    }
}
