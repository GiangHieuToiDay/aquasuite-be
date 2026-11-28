package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.dto.sales.request.QuotationUpsertRequest;
import com.aquasuite.ims.dto.sales.response.QuotationResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderDetailResponse;
import com.aquasuite.ims.enums.sales.QuotationStatus;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.service.sales.QuotationService;
import com.aquasuite.ims.service.sales.SalesOrderService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller, security & validation tests cho UC-12.1 (Create), UC-12.2 (Revisions) & Quotation Convert (UC-13.4).
 */
@WebMvcTest(
        controllers = QuotationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.security\\..*"))
@Import({GlobalExceptionHandler.class, QuotationControllerTest.TestSecurityConfig.class})
class QuotationControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID QUOTATION_ID = UUID.fromString("20000000-0000-4000-8000-000000000002");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private QuotationService quotationService;
    @MockitoBean private SalesOrderService salesOrderService;

    private QuotationResponse sampleQuotation(QuotationStatus status, int revisionNo) {
        return QuotationResponse.builder()
                .quotationId(QUOTATION_ID)
                .quotationCode("QT-202611-0001")
                .revisionNo(revisionNo)
                .status(status)
                .projectName("Dự án AquaSuite")
                .build();
    }

    @Test
    void unauthenticatedRequests_return401() throws Exception {
        mockMvc.perform(post("/api/v1/sales/quotations")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/quotations/preview")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/sales/quotations/{id}/revisions", QUOTATION_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/sales/quotations/{id}/convert-to-order", QUOTATION_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(quotationService, salesOrderService);
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void createPreviewRevisionAndConvert_returnExpectedStatuses() throws Exception {
        when(quotationService.previewCalculation(any(QuotationUpsertRequest.class)))
                .thenReturn(sampleQuotation(QuotationStatus.DRAFT, 1));
        when(quotationService.createQuotation(any(QuotationUpsertRequest.class)))
                .thenReturn(sampleQuotation(QuotationStatus.DRAFT, 1));
        when(quotationService.createRevision(QUOTATION_ID))
                .thenReturn(sampleQuotation(QuotationStatus.DRAFT, 2));
        when(salesOrderService.convertQuotationToOrder(eq(QUOTATION_ID), any()))
                .thenReturn(SalesOrderDetailResponse.builder()
                        .orderId(UUID.randomUUID())
                        .orderCode("SO-202611-0010")
                        .build());

        String validBody = String.format("""
                {"customerId":"%s","projectName":"Biệt thự Thảo Điền"}
                """, CUSTOMER_ID);

        mockMvc.perform(post("/api/v1/quotations/preview")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotationCode").value("QT-202611-0001"));

        mockMvc.perform(post("/api/v1/sales/quotations")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.revisionNo").value(1));

        mockMvc.perform(post("/api/v1/sales/quotations/{id}/revisions", QUOTATION_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.revisionNo").value(2));

        mockMvc.perform(post("/api/v1/sales/quotations/{id}/convert-to-order", QUOTATION_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderCode").value("SO-202611-0010"));

        verify(quotationService).createRevision(QUOTATION_ID);
        verify(salesOrderService).convertQuotationToOrder(eq(QUOTATION_ID), any());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void createAndConvert_validationErrors_return400Sys003() throws Exception {
        mockMvc.perform(post("/api/v1/sales/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectName":"","sentToEmail":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        mockMvc.perform(post("/api/v1/sales/quotations/{id}/convert-to-order", QUOTATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dueDate":"2020-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        verifyNoInteractions(quotationService, salesOrderService);
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
