package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.security.AccessAuthenticationConverter;
import com.aquasuite.ims.security.AuthSecurityConfig;
import com.aquasuite.ims.dto.sales.request.CreateOrderModificationRequest;
import com.aquasuite.ims.dto.sales.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.dto.sales.response.OrderModificationRequestResponse;
import com.aquasuite.ims.enums.sales.OrderModificationRequestStatus;
import com.aquasuite.ims.service.sales.OrderModificationRequestService;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderModificationRequestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {AuthSecurityConfig.class, AccessAuthenticationConverter.class}))
@Import({GlobalExceptionHandler.class, OrderModificationRequestControllerSecurityTest.TestSecurityConfig.class})
class OrderModificationRequestControllerSecurityTest {
    private static final UUID REQUEST_ID = UUID.fromString("10000000-0000-4000-8000-000000000026");

    @Autowired private MockMvc mockMvc;
    @MockBean private OrderModificationRequestService service;
    @MockBean private IamQueryService iamApi;

    @Test
    void unauthenticatedUserCannotReadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/sales/order-modification-requests/{requestId}", REQUEST_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanReadRequest() throws Exception {
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder()
                .id(UUID.randomUUID()).roles(List.of("ACCOUNTANT")).build());
        when(service.detail(eq(REQUEST_ID), eq(true)))
                .thenReturn(OrderModificationRequestResponse.builder().id(REQUEST_ID).build());
        mockMvc.perform(get("/api/v1/sales/order-modification-requests/{requestId}", REQUEST_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(REQUEST_ID.toString()));
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCannotRejectRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sales/order-modification-requests/{requestId}/reject", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decisionNote\":\"Rejected\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffRejectUsesCurrentActor() throws Exception {
        UUID actorId = UUID.randomUUID();
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(service.reject(eq(REQUEST_ID), any(ReviewOrderModificationRequest.class), eq(actorId)))
                .thenReturn(OrderModificationRequestResponse.builder().id(REQUEST_ID).build());
        mockMvc.perform(post("/api/v1/sales/order-modification-requests/{requestId}/reject", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decisionNote\":\"Rejected\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(service).reject(eq(REQUEST_ID), any(ReviewOrderModificationRequest.class), eq(actorId));
    }

    // =============================== ACCOUNTANT detail restriction ===============================

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantBlockedFromViewingPendingReviewDetail() throws Exception {
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder()
                .id(UUID.randomUUID()).roles(List.of("ACCOUNTANT")).build());
        when(service.detail(eq(REQUEST_ID), eq(true)))
                .thenThrow(new AppException(CommonErrorCode.ACCESS_DENIED));
        mockMvc.perform(get("/api/v1/sales/order-modification-requests/{requestId}", REQUEST_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanViewAnyRequestDetail() throws Exception {
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder()
                .id(UUID.randomUUID()).roles(List.of("SHOP_OWNER")).build());
        when(service.detail(eq(REQUEST_ID), eq(false)))
                .thenReturn(OrderModificationRequestResponse.builder().id(REQUEST_ID).build());
        mockMvc.perform(get("/api/v1/sales/order-modification-requests/{requestId}", REQUEST_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCanViewAnyRequestDetail() throws Exception {
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder()
                .id(UUID.randomUUID()).roles(List.of("SALES_STAFF")).build());
        when(service.detail(eq(REQUEST_ID), eq(false)))
                .thenReturn(OrderModificationRequestResponse.builder().id(REQUEST_ID).build());
        mockMvc.perform(get("/api/v1/sales/order-modification-requests/{requestId}", REQUEST_ID))
                .andExpect(status().isOk());
    }

    // =============================== CREATE endpoint ===============================

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCanCreateRequest() throws Exception {
        UUID actorId = UUID.randomUUID();
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(service.create(any(CreateOrderModificationRequest.class), eq(actorId)))
                .thenReturn(OrderModificationRequestResponse.builder().id(UUID.randomUUID()).build());
        mockMvc.perform(post("/api/v1/sales/order-modification-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"10000000-0000-4000-8000-000000000001\","
                                + "\"requestType\":\"ADDRESS_CHANGE\","
                                + "\"customerReason\":\"Change delivery address\","
                                + "\"requestedDeliveryAddress\":\"New Address\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanCreateRequest() throws Exception {
        UUID actorId = UUID.randomUUID();
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(service.create(any(CreateOrderModificationRequest.class), eq(actorId)))
                .thenReturn(OrderModificationRequestResponse.builder().id(UUID.randomUUID()).build());
        mockMvc.perform(post("/api/v1/sales/order-modification-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"10000000-0000-4000-8000-000000000001\","
                                + "\"requestType\":\"FULL_CANCELLATION\","
                                + "\"customerReason\":\"Cancel order\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCannotCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sales/order-modification-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"10000000-0000-4000-8000-000000000001\","
                                + "\"requestType\":\"ADDRESS_CHANGE\","
                                + "\"customerReason\":\"Change delivery address\","
                                + "\"requestedDeliveryAddress\":\"New Address\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sales/order-modification-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"10000000-0000-4000-8000-000000000001\","
                                + "\"requestType\":\"ADDRESS_CHANGE\","
                                + "\"customerReason\":\"Change delivery address\","
                                + "\"requestedDeliveryAddress\":\"New Address\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void createValidatesRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/sales/order-modification-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":null,\"requestType\":null,\"customerReason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // =============================== Tax result / send-result-email ===============================

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanRecordTaxResult() throws Exception {
        UUID actorId = UUID.randomUUID();
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(service.recordTaxResult(eq(REQUEST_ID), any(), eq(actorId)))
                .thenReturn(OrderModificationRequestResponse.builder().id(REQUEST_ID).build());
        mockMvc.perform(post("/api/v1/sales/order-modification-requests/{requestId}/tax-result", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxResultReference\":\"REF-001\","
                                + "\"resultingInvoiceId\":\"10000000-0000-4000-8000-000000000099\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCannotRecordTaxResult() throws Exception {
        mockMvc.perform(post("/api/v1/sales/order-modification-requests/{requestId}/tax-result", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxResultReference\":\"REF-001\","
                                + "\"resultingInvoiceId\":\"10000000-0000-4000-8000-000000000099\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCanSendResultEmail() throws Exception {
        when(iamApi.getCurrentUserId()).thenReturn(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/sales/order-modification-requests/{requestId}/send-result-email", REQUEST_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCannotSendResultEmail() throws Exception {
        mockMvc.perform(post("/api/v1/sales/order-modification-requests/{requestId}/send-result-email", REQUEST_ID))
                .andExpect(status().isForbidden());
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
