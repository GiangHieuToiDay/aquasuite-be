package com.aquasuite.ims.controller.inventory;

import com.aquasuite.ims.dto.inventory.response.GoodsReceiptResponse;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.security.AccessAuthenticationConverter;
import com.aquasuite.ims.security.AuthSecurityConfig;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.GoodsReceiptService;
import com.aquasuite.ims.service.purchasing.PurchaseOrderService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phân quyền UC-32: chỉ SHOP_OWNER và ACCOUNTANT được lập/xác nhận và xem phiếu nhập kho.
 */
@WebMvcTest(
        controllers = GoodsReceiptController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {AuthSecurityConfig.class, AccessAuthenticationConverter.class}))
@Import({GlobalExceptionHandler.class, GoodsReceiptControllerSecurityTest.TestSecurityConfig.class})
class GoodsReceiptControllerSecurityTest {

    private static final String URL = "/api/v1/inventory/goods-receipts";
    private static final String BODY = """
            {"idempotencyKey":"30000000-0000-4000-8000-000000000001",
             "purchaseOrderId":"30000000-0000-4000-8000-000000000002",
             "items":[{"purchaseOrderItemId":"30000000-0000-4000-8000-000000000003","quantity":7}]}
            """;

    @Autowired private MockMvc mockMvc;
    @MockBean private GoodsReceiptService goodsReceiptService;
    @MockBean private PurchaseOrderService purchaseOrderService;
    @MockBean private IamQueryService iamApi;

    private void stubCreate() {
        when(goodsReceiptService.createAndConfirm(any(), any())).thenReturn(
                GoodsReceiptResponse.builder().goodsReceiptId(UUID.randomUUID()).status("CONFIRMED").build());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(goodsReceiptService, never()).createAndConfirm(any(), any());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanCreateReceipt() throws Exception {
        stubCreate();
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanCreateReceipt() throws Exception {
        stubCreate();
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = {"SALES_STAFF", "WAREHOUSE_STAFF", "TECHNICIAN", "SHIPPER", "CUSTOMER"})
    void otherRolesCannotCreateOrReadReceipts() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/receivable/{id}", UUID.randomUUID())).andExpect(status().isForbidden());
        verify(goodsReceiptService, never()).createAndConfirm(any(), any());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void missingIdempotencyKeyIsValidationError() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"purchaseOrderId":"30000000-0000-4000-8000-000000000002",
                         "items":[{"purchaseOrderItemId":"30000000-0000-4000-8000-000000000003","quantity":7}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        verify(goodsReceiptService, never()).createAndConfirm(any(), any());
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
