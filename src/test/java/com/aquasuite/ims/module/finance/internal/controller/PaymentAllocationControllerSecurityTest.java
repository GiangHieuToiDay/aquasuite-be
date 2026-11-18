package com.aquasuite.ims.module.finance.internal.controller;

import com.aquasuite.ims.module.finance.internal.enums.PaymentAllocationStatus;
import com.aquasuite.ims.module.finance.internal.enums.PaymentAllocationType;
import com.aquasuite.ims.module.finance.internal.payload.request.CreatePaymentAllocationRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.ReversePaymentAllocationRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationBatchResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationLineResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationTargetsResponse;
import com.aquasuite.ims.module.finance.internal.service.PaymentAllocationService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.internal.security.AccessAuthenticationConverter;
import com.aquasuite.ims.module.iam.internal.security.AuthSecurityConfig;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {PaymentAllocationController.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {AuthSecurityConfig.class, AccessAuthenticationConverter.class}))
@Import({GlobalExceptionHandler.class, PaymentAllocationControllerSecurityTest.TestSecurityConfig.class})
class PaymentAllocationControllerSecurityTest {

    private static final UUID RECEIPT_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID ALLOCATION_ID = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID ORDER_ID = UUID.fromString("20000000-0000-4000-8000-000000000003");
    private static final UUID CUSTOMER_ID = UUID.fromString("20000000-0000-4000-8000-000000000004");
    private static final UUID INVOICE_ID = UUID.fromString("20000000-0000-4000-8000-000000000005");
    private static final UUID REQUEST_ID = UUID.fromString("20000000-0000-4000-8000-000000000006");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-4000-8000-000000000007");

    private static final String VALID_ALLOCATE_JSON = """
            {
              "allocationRequestId": "20000000-0000-4000-8000-000000000006",
              "allocations": [
                {
                  "invoiceId": "20000000-0000-4000-8000-000000000005",
                  "allocatedAmount": 50.00
                }
              ],
              "reason": "Phân bổ hóa đơn điện tử"
            }
            """;

    private static final String VALID_REVERSE_JSON = """
            {
              "reason": "Điều chỉnh nhầm hóa đơn đợt giao"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentAllocationService paymentAllocationService;

    @MockBean
    private IamApi iamApi;

    @Test
    void unauthenticatedRequestsAreRejectedAcrossAllEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/finance/receipts/unallocated"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/finance/receipts/{receiptId}/allocation-targets", RECEIPT_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/finance/receipts/{receiptId}/allocations", RECEIPT_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/finance/receipts/{receiptId}/allocations", RECEIPT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ALLOCATE_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/finance/receipts/{receiptId}/allocations/{allocationId}/reverse", RECEIPT_ID, ALLOCATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REVERSE_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanAccessAllFiveAllocationEndpoints() throws Exception {
        when(iamApi.getCurrentUserId()).thenReturn(ACTOR_ID);
        when(paymentAllocationService.listUnallocatedReceipts(any())).thenReturn(Page.empty());
        when(paymentAllocationService.getTargets(RECEIPT_ID)).thenReturn(
                PaymentAllocationTargetsResponse.builder()
                        .receiptId(RECEIPT_ID)
                        .receiptCode("PAY-001")
                        .orderId(ORDER_ID)
                        .customerId(CUSTOMER_ID)
                        .receiptAmount(new BigDecimal("100.00"))
                        .allocatedAmount(BigDecimal.ZERO)
                        .remainingAmount(new BigDecimal("100.00"))
                        .invoices(List.of())
                        .build());
        when(paymentAllocationService.getAllocations(RECEIPT_ID)).thenReturn(List.of());
        when(paymentAllocationService.allocate(eq(RECEIPT_ID), any(CreatePaymentAllocationRequest.class), eq(ACTOR_ID)))
                .thenReturn(PaymentAllocationBatchResponse.builder()
                        .allocationRequestId(REQUEST_ID)
                        .receiptId(RECEIPT_ID)
                        .orderId(ORDER_ID)
                        .allocatedAmount(new BigDecimal("50.00"))
                        .remainingReceiptAmount(new BigDecimal("50.00"))
                        .allocations(List.of())
                        .build());
        when(paymentAllocationService.reverse(eq(RECEIPT_ID), eq(ALLOCATION_ID), any(ReversePaymentAllocationRequest.class), eq(ACTOR_ID)))
                .thenReturn(PaymentAllocationLineResponse.builder()
                        .allocationId(ALLOCATION_ID)
                        .allocationRequestId(REQUEST_ID)
                        .transactionId(RECEIPT_ID)
                        .orderId(ORDER_ID)
                        .invoiceId(INVOICE_ID)
                        .allocatedAmount(new BigDecimal("50.00"))
                        .allocationType(PaymentAllocationType.MANUAL)
                        .allocationStatus(PaymentAllocationStatus.REVERSED)
                        .reversedBy(ACTOR_ID)
                        .reversalReason("Điều chỉnh nhầm hóa đơn đợt giao")
                        .build());

        mockMvc.perform(get("/api/v1/finance/receipts/unallocated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/finance/receipts/{receiptId}/allocation-targets", RECEIPT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.receiptCode").value("PAY-001"));

        mockMvc.perform(get("/api/v1/finance/receipts/{receiptId}/allocations", RECEIPT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/finance/receipts/{receiptId}/allocations", RECEIPT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ALLOCATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allocatedAmount").value(50.00));

        mockMvc.perform(post("/api/v1/finance/receipts/{receiptId}/allocations/{allocationId}/reverse", RECEIPT_ID, ALLOCATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REVERSE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allocationStatus").value("REVERSED"));

        verify(paymentAllocationService).allocate(eq(RECEIPT_ID), any(CreatePaymentAllocationRequest.class), eq(ACTOR_ID));
        verify(paymentAllocationService).reverse(eq(RECEIPT_ID), eq(ALLOCATION_ID), any(ReversePaymentAllocationRequest.class), eq(ACTOR_ID));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerIsForbiddenAcrossAllFiveEndpoints() throws Exception {
        assertAllFiveEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffIsForbiddenAcrossAllFiveEndpoints() throws Exception {
        assertAllFiveEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerIsForbiddenAcrossAllFiveEndpoints() throws Exception {
        assertAllFiveEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void allocateRejectsNullAllocationRequestIdWithSys003() throws Exception {
        mockMvc.perform(post("/api/v1/finance/receipts/{receiptId}/allocations", RECEIPT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allocations": [
                                    {
                                      "invoiceId": "20000000-0000-4000-8000-000000000005",
                                      "allocatedAmount": 50.00
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        verify(paymentAllocationService, never()).allocate(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void allocateRejectsEmptyAllocationsWithSys003() throws Exception {
        mockMvc.perform(post("/api/v1/finance/receipts/{receiptId}/allocations", RECEIPT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allocationRequestId": "20000000-0000-4000-8000-000000000006",
                                  "allocations": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        verify(paymentAllocationService, never()).allocate(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void allocateRejectsNonPositiveAllocatedAmountWithSys003() throws Exception {
        mockMvc.perform(post("/api/v1/finance/receipts/{receiptId}/allocations", RECEIPT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allocationRequestId": "20000000-0000-4000-8000-000000000006",
                                  "allocations": [
                                    {
                                      "invoiceId": "20000000-0000-4000-8000-000000000005",
                                      "allocatedAmount": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        verify(paymentAllocationService, never()).allocate(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void reverseRejectsBlankReasonWithSys003() throws Exception {
        mockMvc.perform(post("/api/v1/finance/receipts/{receiptId}/allocations/{allocationId}/reverse", RECEIPT_ID, ALLOCATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        verify(paymentAllocationService, never()).reverse(any(), any(), any(), any());
    }

    private void assertAllFiveEndpointsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/finance/receipts/unallocated"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        mockMvc.perform(get("/api/v1/finance/receipts/{receiptId}/allocation-targets", RECEIPT_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        mockMvc.perform(get("/api/v1/finance/receipts/{receiptId}/allocations", RECEIPT_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        mockMvc.perform(post("/api/v1/finance/receipts/{receiptId}/allocations", RECEIPT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ALLOCATE_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        mockMvc.perform(post("/api/v1/finance/receipts/{receiptId}/allocations/{allocationId}/reverse", RECEIPT_ID, ALLOCATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REVERSE_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
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
