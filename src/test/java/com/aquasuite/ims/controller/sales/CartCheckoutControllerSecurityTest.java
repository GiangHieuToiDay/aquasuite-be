package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.dto.sales.request.PlaceOnlineOrderRequest;
import com.aquasuite.ims.dto.sales.response.CartResponse;
import com.aquasuite.ims.dto.sales.response.OnlineCheckoutPreviewResponse;
import com.aquasuite.ims.dto.sales.response.OnlineOrderCheckoutResponse;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.service.sales.CartCheckoutService;
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
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CartCheckoutController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.security\\..*"))
@Import({GlobalExceptionHandler.class, CartCheckoutControllerSecurityTest.TestSecurityConfig.class})
class CartCheckoutControllerSecurityTest {

    private static final UUID ORDER_ID = UUID.fromString("10000000-0000-4000-8000-000000000018");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private CartCheckoutService cartCheckoutService;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/sales/cart")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/sales/online-orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/sales/online-orders/checkout-preview")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/sales/online-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"deliveryAddress\":\"123 Le Loi\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cartCheckoutService);
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCanAccessCartButCannotUseCustomerOnlineCheckoutEndpoints() throws Exception {
        when(cartCheckoutService.clearCart()).thenReturn(CartResponse.builder().totalItems(0).build());

        mockMvc.perform(delete("/api/v1/sales/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã làm trống giỏ hàng"))
                .andExpect(jsonPath("$.data.totalItems").value(0));

        mockMvc.perform(get("/api/v1/sales/online-orders")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/sales/online-orders/checkout-preview")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sales/online-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"deliveryAddress\":\"123 Le Loi\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/sales/online-orders/{orderId}", ORDER_ID)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sales/online-orders/{orderId}/payos/verify", ORDER_ID)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sales/online-orders/{orderId}/payos/payment-link", ORDER_ID)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCanAccessAllOnlineCheckoutEndpointsAndRetiredCheckoutPathsReturnNotFound() throws Exception {
        OnlineOrderCheckoutResponse orderResponse = OnlineOrderCheckoutResponse.builder()
                .orderId(ORDER_ID)
                .orderCode("SO-UC18-001")
                .build();
        when(cartCheckoutService.getMyOnlineOrders()).thenReturn(List.of(orderResponse));
        when(cartCheckoutService.getCheckoutPreview()).thenReturn(new OnlineCheckoutPreviewResponse());
        when(cartCheckoutService.placeOnlineOrder(any(PlaceOnlineOrderRequest.class))).thenReturn(orderResponse);
        when(cartCheckoutService.getOnlineOrderDetail(ORDER_ID)).thenReturn(orderResponse);
        when(cartCheckoutService.verifyAndSyncPayosPayment(ORDER_ID)).thenReturn(orderResponse);
        when(cartCheckoutService.createOrRefreshPayosPaymentLink(ORDER_ID)).thenReturn(orderResponse);

        mockMvc.perform(get("/api/v1/sales/online-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderCode").value("SO-UC18-001"));
        mockMvc.perform(get("/api/v1/sales/online-orders/checkout-preview"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sales/online-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"deliveryAddress\":\"123 Le Loi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCode").value("SO-UC18-001"));
        mockMvc.perform(get("/api/v1/sales/online-orders/{orderId}", ORDER_ID))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sales/online-orders/{orderId}/payos/verify", ORDER_ID))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sales/online-orders/{orderId}/payos/payment-link", ORDER_ID))
                .andExpect(status().isOk());

        Set<String> registeredPaths = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(registeredPaths).doesNotContain(
                "/api/v1/sales/checkout/preview",
                "/api/v1/sales/checkout/place-order"
        );
        assertThat(registeredPaths).contains(
                "/api/v1/sales/cart",
                "/api/v1/sales/cart/items",
                "/api/v1/sales/cart/items/merge",
                "/api/v1/sales/cart/items/{itemId}",
                "/api/v1/sales/online-orders",
                "/api/v1/sales/online-orders/checkout-preview",
                "/api/v1/sales/online-orders/{orderId}",
                "/api/v1/sales/online-orders/{orderId}/payos/verify",
                "/api/v1/sales/online-orders/{orderId}/payos/payment-link"
        );

        verify(cartCheckoutService).getMyOnlineOrders();
        verify(cartCheckoutService).getCheckoutPreview();
        verify(cartCheckoutService).placeOnlineOrder(any(PlaceOnlineOrderRequest.class));
        verify(cartCheckoutService).getOnlineOrderDetail(ORDER_ID);
        verify(cartCheckoutService).verifyAndSyncPayosPayment(ORDER_ID);
        verify(cartCheckoutService).createOrRefreshPayosPaymentLink(ORDER_ID);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .httpBasic(httpBasic -> { })
                    .build();
        }
    }
}

