package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.dto.sales.request.ConvertQuotationToOrderRequest;
import com.aquasuite.ims.dto.sales.response.SalesOrderDetailResponse;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.service.iam.IamQueryService;
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
 * Security & validation tests cho UC-13.4 (/api/v1/sales/orders/from-quotation/{quotationId}).
 */
@WebMvcTest(
        controllers = SalesOrderController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.security\\..*"))
@Import({GlobalExceptionHandler.class, SalesOrderControllerSecurityTest.TestSecurityConfig.class})
class SalesOrderControllerSecurityTest {

    private static final UUID QUOTATION_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final String URL = "/api/v1/sales/orders/from-quotation/" + QUOTATION_ID;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SalesOrderService salesOrderService;
    @MockitoBean private IamQueryService iamApi;

    private void stubConversion() {
        when(salesOrderService.convertQuotationToOrder(eq(QUOTATION_ID), any()))
                .thenReturn(SalesOrderDetailResponse.builder()
                        .orderId(UUID.randomUUID())
                        .orderCode("SO-20261127-0001")
                        .orderStatus("PROCESSING")
                        .build());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(salesOrderService);
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaff_canConvertQuotationToOrder() throws Exception {
        stubConversion();
        mockMvc.perform(post(URL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderCode").value("SO-20261127-0001"));
        verify(salesOrderService).convertQuotationToOrder(eq(QUOTATION_ID), any());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwner_canConvertQuotationToOrder() throws Exception {
        stubConversion();
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"note":"Khách đã cọc","reserveStockAfterDeposit":true}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
        verify(salesOrderService).convertQuotationToOrder(eq(QUOTATION_ID), any(ConvertQuotationToOrderRequest.class));
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT", "CUSTOMER", "WAREHOUSE_STAFF", "TECHNICIAN", "SHIPPER"})
    void otherRoles_cannotConvertQuotationToOrder() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(salesOrderService);
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void pastDueDate_returns400ValidationError() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"dueDate":"2020-01-01"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        verifyNoInteractions(salesOrderService);
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
