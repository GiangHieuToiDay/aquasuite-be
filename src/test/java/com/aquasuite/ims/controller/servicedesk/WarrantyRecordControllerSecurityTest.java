package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyRecordFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordListItemResponse;
import com.aquasuite.ims.enums.servicedesk.WarrantyDisplayStatus;
import com.aquasuite.ims.enums.servicedesk.WarrantyStatus;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.service.servicedesk.WarrantyRecordService;
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
        controllers = WarrantyRecordController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.security\\..*"))
@Import({GlobalExceptionHandler.class, WarrantyRecordControllerSecurityTest.TestSecurityConfig.class})
class WarrantyRecordControllerSecurityTest {

    private static final UUID RECORD_ID = UUID.fromString("40000000-0000-4000-8000-000000000048");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarrantyRecordService warrantyRecordService;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/warranty-records"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/warranty-records/{id}", RECORD_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(warrantyRecordService);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanSearchAndReadWarrantyRecordDetail() throws Exception {
        stubStaffSearchAndDetail();

        mockMvc.perform(get("/api/v1/warranty-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].warrantyCode").value("WAR-202611-0001"));

        mockMvc.perform(get("/api/v1/warranty-records/{id}", RECORD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warrantyCode").value("WAR-202611-0001"));

        verify(warrantyRecordService).searchRecords(any(WarrantyRecordFilterRequest.class));
        verify(warrantyRecordService).getRecordDetail(RECORD_ID);
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCanSearchAndReadWarrantyRecordDetail() throws Exception {
        stubStaffSearchAndDetail();

        mockMvc.perform(get("/api/v1/warranty-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/warranty-records/{id}", RECORD_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TECHNICAL_STAFF")
    void technicalStaffIsForbiddenFromWarrantyRecordStaffEndpoints() throws Exception {
        assertStaffEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantIsForbiddenFromWarrantyRecordStaffEndpoints() throws Exception {
        assertStaffEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerIsForbiddenFromWarrantyRecordStaffEndpoints() throws Exception {
        assertStaffEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "SHIPPER")
    void shipperIsForbiddenFromWarrantyRecordStaffEndpoints() throws Exception {
        assertStaffEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void searchRejectsInvalidFilterSerialNumber() throws Exception {
        mockMvc.perform(get("/api/v1/warranty-records").param("serialNumber", "A".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        verifyNoInteractions(warrantyRecordService);
    }

    private void stubStaffSearchAndDetail() {
        WarrantyRecordListItemResponse item = WarrantyRecordListItemResponse.builder()
                .warrantyRecordId(RECORD_ID)
                .warrantyCode("WAR-202611-0001")
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
                .build();
        when(warrantyRecordService.searchRecords(any(WarrantyRecordFilterRequest.class)))
                .thenReturn(PageResponse.of(List.of(item), 0, 20, 1, 1));
        when(warrantyRecordService.getRecordDetail(RECORD_ID))
                .thenReturn(detail);
    }

    private void assertStaffEndpointsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/warranty-records"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        mockMvc.perform(get("/api/v1/warranty-records/{id}", RECORD_ID))
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
