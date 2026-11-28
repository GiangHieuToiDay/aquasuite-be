package com.aquasuite.ims.controller.inventory;

import com.aquasuite.ims.dto.inventory.response.InventoryInboundKpiResponse;
import com.aquasuite.ims.dto.inventory.response.VariantInboundDetailResponse;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.security.AccessAuthenticationConverter;
import com.aquasuite.ims.security.AuthSecurityConfig;
import com.aquasuite.ims.service.inventory.InventoryInboundTrackingService;
import org.junit.jupiter.api.DisplayName;
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
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = InventoryInboundTrackingController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {AuthSecurityConfig.class, AccessAuthenticationConverter.class}))
@Import({GlobalExceptionHandler.class, InventoryInboundTrackingControllerSecurityTest.TestSecurityConfig.class})
class InventoryInboundTrackingControllerSecurityTest {

    private static final UUID VARIANT_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");

    @Autowired private MockMvc mockMvc;

    @MockBean private InventoryInboundTrackingService inboundTrackingService;

    @Test
    @DisplayName("AC-05: Chưa xác thực trả 401")
    void unauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/inbound-tracking/kpi-overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    @DisplayName("AC-05: SALES_STAFF không được phép truy cập (403)")
    void salesStaffForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/inbound-tracking/kpi-overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SHIPPER")
    @DisplayName("AC-05: SHIPPER không được phép truy cập (403)")
    void shipperForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/inbound-tracking/summaries"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    @DisplayName("AC-05: SHOP_OWNER truy cập được toàn bộ các endpoint inbound-tracking")
    void shopOwnerCanAccessAllEndpoints() throws Exception {
        when(inboundTrackingService.getKpiOverview()).thenReturn(
                InventoryInboundKpiResponse.builder().totalSkus(3).needOrderCount(1).totalIncomingQty(BigDecimal.TEN).build());
        when(inboundTrackingService.getInboundSummaries(any())).thenReturn(Collections.emptyMap());
        when(inboundTrackingService.getVariantPurchaseOrders(eq(VARIANT_ID), anyBoolean())).thenReturn(
                VariantInboundDetailResponse.builder()
                        .variantId(VARIANT_ID)
                        .incomingQty(new BigDecimal("20"))
                        .purchaseOrders(Collections.emptyList())
                        .build());
        when(inboundTrackingService.getReadableMovements(eq(VARIANT_ID), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/inventory/inbound-tracking/kpi-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalSkus").value(3));

        mockMvc.perform(get("/api/v1/inventory/inbound-tracking/summaries")
                        .param("variantIds", VARIANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/inventory/inbound-tracking/variants/{variantId}/purchase-orders", VARIANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incomingQty").value(20));

        mockMvc.perform(get("/api/v1/inventory/inbound-tracking/variants/{variantId}/movements", VARIANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    @DisplayName("AC-05: ACCOUNTANT cũng được phép xem tiến độ đơn nhập kho và thẻ kho")
    void accountantCanAccessTrackingEndpoints() throws Exception {
        when(inboundTrackingService.getKpiOverview()).thenReturn(
                InventoryInboundKpiResponse.builder().totalSkus(5).build());

        mockMvc.perform(get("/api/v1/inventory/inbound-tracking/kpi-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSkus").value(5));
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
