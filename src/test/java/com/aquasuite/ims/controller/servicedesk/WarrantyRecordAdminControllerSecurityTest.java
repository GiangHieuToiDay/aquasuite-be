package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.dto.servicedesk.request.CreateAdHocWarrantyRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyFromOrderRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordDetailResponse;
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
import org.springframework.http.MediaType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security & validation tests cho UC-21.2 (Issue), UC-21.3 (Search), UC-21.4 (Void).
 */
@WebMvcTest(
        controllers = WarrantyRecordController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.security\\..*"))
@Import({GlobalExceptionHandler.class, WarrantyRecordAdminControllerSecurityTest.TestSecurityConfig.class})
class WarrantyRecordAdminControllerSecurityTest {

    private static final UUID RECORD_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID ORDER_ITEM_ID = UUID.fromString("50000000-0000-4000-8000-000000000002");
    private static final UUID CUSTOMER_ID = UUID.fromString("50000000-0000-4000-8000-000000000003");
    private static final UUID VARIANT_ID = UUID.fromString("50000000-0000-4000-8000-000000000004");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WarrantyRecordService warrantyRecordService;

    private WarrantyRecordDetailResponse sampleRecord(WarrantyStatus status) {
        return WarrantyRecordDetailResponse.builder()
                .warrantyRecordId(RECORD_ID)
                .warrantyCode("WAR-202611-0001")
                .status(status)
                .build();
    }

    @Test
    void unauthenticatedRequests_return401() throws Exception {
        mockMvc.perform(get("/api/v1/warranty-records")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/warranty-records/{id}", RECORD_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/warranty-records/from-order")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/warranty-records/ad-hoc")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/warranty-records/{id}/void", RECORD_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sai\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(warrantyRecordService);
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaff_canSearchReadAndIssue_butCannotVoid() throws Exception {
        when(warrantyRecordService.searchRecords(any())).thenReturn(PageResponse.of(List.of(), 0, 20, 0, 0));
        when(warrantyRecordService.getRecordDetail(RECORD_ID)).thenReturn(sampleRecord(WarrantyStatus.ACTIVE));
        when(warrantyRecordService.createFromOrder(any(CreateWarrantyFromOrderRequest.class)))
                .thenReturn(sampleRecord(WarrantyStatus.ACTIVE));
        when(warrantyRecordService.createAdHoc(any(CreateAdHocWarrantyRequest.class)))
                .thenReturn(sampleRecord(WarrantyStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/warranty-records")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/warranty-records/{id}", RECORD_ID)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/warranty-records/from-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"orderItemId":"%s","serialNumber":"SN-100","startDate":"2026-11-12"}
                                """, ORDER_ITEM_ID)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/warranty-records/ad-hoc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"customerId":"%s","variantId":"%s","serialNumber":"SN-101","startDate":"2026-11-12"}
                                """, CUSTOMER_ID, VARIANT_ID)))
                .andExpect(status().isCreated());

        // Void chỉ dành cho SHOP_OWNER
        mockMvc.perform(post("/api/v1/warranty-records/{id}/void", RECORD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Nhập nhầm serial\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwner_canVoidRecord() throws Exception {
        when(warrantyRecordService.voidRecord(eq(RECORD_ID), eq("Khách trả hàng")))
                .thenReturn(sampleRecord(WarrantyStatus.VOIDED));

        mockMvc.perform(post("/api/v1/warranty-records/{id}/void", RECORD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Khách trả hàng\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED"));
        verify(warrantyRecordService).voidRecord(RECORD_ID, "Khách trả hàng");
    }

    @Test
    @WithMockUser(roles = {"CUSTOMER", "ACCOUNTANT", "TECHNICIAN", "SHIPPER"})
    void nonSalesOrOwnerRoles_areForbiddenOnAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/warranty-records")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/warranty-records/ad-hoc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"customerId":"%s","variantId":"%s","startDate":"2026-11-12"}
                                """, CUSTOMER_ID, VARIANT_ID)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(warrantyRecordService);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void validationErrors_return400Sys003() throws Exception {
        mockMvc.perform(post("/api/v1/warranty-records/from-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"orderItemId":"%s","startDate":"2099-01-01"}
                                """, ORDER_ITEM_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        mockMvc.perform(post("/api/v1/warranty-records/{id}/void", RECORD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        verifyNoInteractions(warrantyRecordService);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(httpBasic -> { })
                    .build();
        }
    }
}
