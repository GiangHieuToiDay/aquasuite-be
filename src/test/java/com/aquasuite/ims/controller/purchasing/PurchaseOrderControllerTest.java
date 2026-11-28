package com.aquasuite.ims.controller.purchasing;

import com.aquasuite.ims.dto.purchasing.request.CancelPurchaseOrderRequest;
import com.aquasuite.ims.dto.purchasing.request.ClosePurchaseOrderRequest;
import com.aquasuite.ims.dto.purchasing.request.CreatePurchaseOrderRequest;
import com.aquasuite.ims.dto.purchasing.request.UpdatePurchaseOrderRequest;
import com.aquasuite.ims.dto.purchasing.response.PurchaseOrderResponse;
import com.aquasuite.ims.enums.purchasing.PurchaseOrderStatus;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.service.purchasing.PurchaseOrderService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security & validation tests cho UC-14.3 (Create PO), UC-14.4 (Edit PO), UC-14.5 (Cancel/Close PO).
 */
@WebMvcTest(
        controllers = PurchaseOrderController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.security\\..*"))
@Import({GlobalExceptionHandler.class, PurchaseOrderControllerTest.TestSecurityConfig.class})
class PurchaseOrderControllerTest {

    private static final UUID PO_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID SUPPLIER_ID = UUID.fromString("30000000-0000-4000-8000-000000000002");
    private static final UUID VARIANT_ID = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final String BASE_URL = "/api/v1/purchasing/purchase-orders";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PurchaseOrderService purchaseOrderService;

    private String validCreateJson() {
        return String.format("""
                {"supplierId":"%s","warehouseCode":"WH_MAIN",
                 "items":[{"variantId":"%s","quantity":5}]}
                """, SUPPLIER_ID, VARIANT_ID);
    }

    private String validUpdateJson() {
        return String.format("""
                {"version":0,"items":[{"variantId":"%s","quantity":5,"unitCost":500000.00}]}
                """, VARIANT_ID);
    }

    @Test
    void unauthenticatedRequests_return401() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(validCreateJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE_URL + "/{id}", PO_ID).contentType(MediaType.APPLICATION_JSON).content(validUpdateJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE_URL + "/{id}/cancel", PO_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cancelReason\":\"Hủy\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE_URL + "/{id}/close", PO_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closeReason\":\"Đóng\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(purchaseOrderService);
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaff_canCreateAndEditDraft_butCannotCancelOrClose() throws Exception {
        when(purchaseOrderService.createDraft(any(CreatePurchaseOrderRequest.class)))
                .thenReturn(PurchaseOrderResponse.builder().purchaseOrderId(PO_ID).status(PurchaseOrderStatus.DRAFT).build());
        when(purchaseOrderService.updateDraft(eq(PO_ID), any(UpdatePurchaseOrderRequest.class)))
                .thenReturn(PurchaseOrderResponse.builder().purchaseOrderId(PO_ID).status(PurchaseOrderStatus.DRAFT).build());

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(validCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(put(BASE_URL + "/{id}", PO_ID).contentType(MediaType.APPLICATION_JSON).content(validUpdateJson()))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE_URL + "/{id}/cancel", PO_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cancelReason\":\"Hủy PO\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE_URL + "/{id}/close", PO_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closeReason\":\"Chốt thiếu\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountant_canCreateAndEditDraft_butCannotCancelOrClose() throws Exception {
        when(purchaseOrderService.createDraft(any(CreatePurchaseOrderRequest.class)))
                .thenReturn(PurchaseOrderResponse.builder().purchaseOrderId(PO_ID).status(PurchaseOrderStatus.DRAFT).build());

        mockMvc.perform(post("/api/v1/purchase-orders").contentType(MediaType.APPLICATION_JSON).content(validCreateJson()))
                .andExpect(status().isCreated());
        mockMvc.perform(post(BASE_URL + "/{id}/cancel", PO_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cancelReason\":\"Hủy PO\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE_URL + "/{id}/close", PO_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closeReason\":\"Chốt thiếu\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwner_canCancelAndClose() throws Exception {
        when(purchaseOrderService.cancelPurchaseOrder(eq(PO_ID), any(CancelPurchaseOrderRequest.class)))
                .thenReturn(PurchaseOrderResponse.builder().purchaseOrderId(PO_ID).status(PurchaseOrderStatus.CANCELLED).build());
        when(purchaseOrderService.closePurchaseOrder(eq(PO_ID), any(ClosePurchaseOrderRequest.class)))
                .thenReturn(PurchaseOrderResponse.builder().purchaseOrderId(PO_ID).status(PurchaseOrderStatus.COMPLETED).build());

        mockMvc.perform(post(BASE_URL + "/{id}/cancel", PO_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cancelReason\":\"NCC hết hàng\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post(BASE_URL + "/{id}/close", PO_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closeReason\":\"Chốt đủ đợt 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        verify(purchaseOrderService).cancelPurchaseOrder(eq(PO_ID), any(CancelPurchaseOrderRequest.class));
        verify(purchaseOrderService).closePurchaseOrder(eq(PO_ID), any(ClosePurchaseOrderRequest.class));
    }

    @Test
    @WithMockUser(roles = {"WAREHOUSE_STAFF", "CUSTOMER", "SHIPPER", "TECHNICIAN"})
    void nonPurchasingRoles_areForbiddenOnCreateAndEdit() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(validCreateJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(BASE_URL + "/{id}", PO_ID).contentType(MediaType.APPLICATION_JSON).content(validUpdateJson()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(purchaseOrderService);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void validationErrors_createUpdateCancelClose_return400Sys003() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        mockMvc.perform(put(BASE_URL + "/{id}", PO_ID).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        mockMvc.perform(post(BASE_URL + "/{id}/cancel", PO_ID).contentType(MediaType.APPLICATION_JSON).content("{\"cancelReason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        mockMvc.perform(post(BASE_URL + "/{id}/close", PO_ID).contentType(MediaType.APPLICATION_JSON).content("{\"closeReason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        verifyNoInteractions(purchaseOrderService);
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
