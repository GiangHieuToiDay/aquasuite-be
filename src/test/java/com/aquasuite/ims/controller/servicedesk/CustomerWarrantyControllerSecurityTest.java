package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.enums.servicedesk.WarrantyDisplayStatus;
import com.aquasuite.ims.enums.servicedesk.WarrantyStatus;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyRecordFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordListItemResponse;
import com.aquasuite.ims.service.servicedesk.WarrantyRecordService;
import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CustomerWarrantyController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.module\\.iam\\.internal\\.security\\..*"))
@Import({GlobalExceptionHandler.class, CustomerWarrantyControllerSecurityTest.TestSecurityConfig.class})
class CustomerWarrantyControllerSecurityTest {

    private static final UUID RECORD_ID = UUID.fromString("20000000-0000-4000-8000-000000000048");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarrantyRecordService warrantyRecordService;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/storefront/warranty-records"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/storefront/warranty-records/{id}", RECORD_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(warrantyRecordService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCanListAndReadOwnStorefrontWarrantyRecords() throws Exception {
        WarrantyRecordListItemResponse item = WarrantyRecordListItemResponse.builder()
                .warrantyRecordId(RECORD_ID)
                .warrantyCode("WAR-202611-0001")
                .sku("CS886")
                .productName("Bồn cầu TOTO")
                .serialNumber("SN-CS886-001")
                .orderCode("ORD-202611-001")
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2028, 11, 1))
                .remainingDays(716L)
                .warrantyMonths(24)
                .status(WarrantyStatus.ACTIVE)
                .displayStatus(WarrantyDisplayStatus.ACTIVE)
                .canRequestSupport(true)
                .build();
        WarrantyRecordDetailResponse detail = WarrantyRecordDetailResponse.builder()
                .warrantyRecordId(RECORD_ID)
                .warrantyCode("WAR-202611-0001")
                .status(WarrantyStatus.ACTIVE)
                .displayStatus(WarrantyDisplayStatus.ACTIVE)
                .canRequestSupport(true)
                .remainingDays(716L)
                .serialNumber("SN-CS886-001")
                .build();

        when(warrantyRecordService.getMyRecords(any(WarrantyRecordFilterRequest.class)))
                .thenReturn(PageResponse.of(List.of(item), 0, 20, 1, 1));
        when(warrantyRecordService.getMyRecordDetail(RECORD_ID))
                .thenReturn(detail);

        mockMvc.perform(get("/api/v1/storefront/warranty-records?keyword=ORD-202611-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].orderCode").value("ORD-202611-001"))
                .andExpect(jsonPath("$.data.items[0].remainingDays").value(716))
                .andExpect(jsonPath("$.data.items[0].displayStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].canRequestSupport").value(true));

        mockMvc.perform(get("/api/v1/storefront/warranty-records/{id}", RECORD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warrantyCode").value("WAR-202611-0001"))
                .andExpect(jsonPath("$.data.displayStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.canRequestSupport").value(true));

        verify(warrantyRecordService).getMyRecords(any(WarrantyRecordFilterRequest.class));
        verify(warrantyRecordService).getMyRecordDetail(RECORD_ID);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCannotUseCustomerStorefrontEndpoint() throws Exception {
        assertStorefrontEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCannotUseCustomerStorefrontEndpoint() throws Exception {
        assertStorefrontEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCannotUseCustomerStorefrontEndpoint() throws Exception {
        assertStorefrontEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "TECHNICAL_STAFF")
    void technicalStaffCannotUseCustomerStorefrontEndpoint() throws Exception {
        assertStorefrontEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "SHIPPER")
    void shipperCannotUseCustomerStorefrontEndpoint() throws Exception {
        assertStorefrontEndpointsForbidden();
    }

    private void assertStorefrontEndpointsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/storefront/warranty-records"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        mockMvc.perform(get("/api/v1/storefront/warranty-records/{id}", RECORD_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        verifyNoInteractions(warrantyRecordService);
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
