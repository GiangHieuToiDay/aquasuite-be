package com.aquasuite.ims.module.inventory.internal.controller;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.internal.security.AccessAuthenticationConverter;
import com.aquasuite.ims.module.iam.internal.security.AuthSecurityConfig;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.inventory.internal.service.InventoryService;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử phân quyền @PreAuthorize của InventoryController (UC-31) bằng @WebMvcTest + method security.
 * Không cần Docker/CSDL: toàn bộ tầng service được mock.
 * Xác minh: tra cứu dành cho SHOP_OWNER + ACCOUNTANT; cấu hình ngưỡng chỉ SHOP_OWNER (AC-06, AC-07).
 */
@WebMvcTest(
        controllers = InventoryController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {AuthSecurityConfig.class, AccessAuthenticationConverter.class}))
@Import({GlobalExceptionHandler.class, InventoryControllerSecurityTest.TestSecurityConfig.class})
class InventoryControllerSecurityTest {

    private static final UUID VARIANT_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired private MockMvc mockMvc;

    @MockBean private InventoryService inventoryService;
    @MockBean private IamApi iamApi;

    // ---- Tra cứu danh sách: SHOP_OWNER + ACCOUNTANT được phép ----

    @Test
    void unauthenticatedSearchIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/inventory")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanSearchInventory() throws Exception {
        when(inventoryService.searchInventory(any(), any(), anyBoolean(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/inventory")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanSearchInventory() throws Exception {
        when(inventoryService.searchInventory(any(), any(), anyBoolean(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/inventory")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCannotSearchInventory() throws Exception {
        mockMvc.perform(get("/api/v1/inventory")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanViewLowStock() throws Exception {
        when(inventoryService.getLowStockItems(any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/inventory/low-stock")).andExpect(status().isOk());
    }

    // ---- Cấu hình ngưỡng: CHỈ SHOP_OWNER ----

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCannotUpdateThreshold() throws Exception {
        mockMvc.perform(patch("/api/v1/inventory/{variantId}/threshold", VARIANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minimumQty\":10,\"maximumQty\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCannotUpdateThreshold() throws Exception {
        mockMvc.perform(patch("/api/v1/inventory/{variantId}/threshold", VARIANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minimumQty\":10,\"maximumQty\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanUpdateThreshold() throws Exception {
        UUID actorId = UUID.fromString("20000000-0000-4000-8000-000000000009");
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(inventoryService.updateThreshold(any(), any(), any(), any())).thenReturn(
                StockSummaryDto.builder().variantId(VARIANT_ID).minimumQty(BigDecimal.TEN)
                        .maximumQty(BigDecimal.valueOf(100)).build());

        mockMvc.perform(patch("/api/v1/inventory/{variantId}/threshold", VARIANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minimumQty\":10,\"maximumQty\":100}"))
                .andExpect(status().isOk());

        verify(inventoryService).updateThreshold(any(), any(), any(), any());
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
