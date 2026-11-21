package com.aquasuite.ims.controller.finance;

import com.aquasuite.ims.enums.finance.DebtStatus;
import com.aquasuite.ims.dto.finance.request.DebtCallResultRequest;
import com.aquasuite.ims.dto.finance.request.DebtReminderRequest;
import com.aquasuite.ims.dto.finance.request.ReasonRequest;
import com.aquasuite.ims.dto.finance.response.CustomerCreditSummaryResponse;
import com.aquasuite.ims.dto.finance.response.CustomerDebtPageResponse;
import com.aquasuite.ims.dto.finance.response.DebtReminderResponse;
import com.aquasuite.ims.service.finance.CustomerDebtService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.security.AccessAuthenticationConverter;
import com.aquasuite.ims.security.AuthSecurityConfig;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {CustomerDebtController.class, CreditControlController.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {AuthSecurityConfig.class, AccessAuthenticationConverter.class}))
@Import({GlobalExceptionHandler.class, CustomerDebtControllerSecurityTest.TestSecurityConfig.class})
class CustomerDebtControllerSecurityTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("10000000-0000-4000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerDebtService customerDebtService;

    @MockBean
    private IamQueryService iamApi;

    @Test
    void unauthenticatedDebtDashboardIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/finance/customer-debts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanReadDashboard() throws Exception {
        when(customerDebtService.list(null, null, null, "customerCode", "asc", 0, 20))
                .thenReturn(new CustomerDebtPageResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/finance/customer-debts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanReadDashboard() throws Exception {
        when(customerDebtService.list(null, null, null, "customerCode", "asc", 0, 20))
                .thenReturn(new CustomerDebtPageResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/finance/customer-debts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCannotReadDebtManagementDashboardOrDetail() throws Exception {
        mockMvc.perform(get("/api/v1/finance/customer-debts"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/finance/customer-debts/{customerId}", CUSTOMER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCanReadOnlyMinimalCreditSummaryForPos() throws Exception {
        when(customerDebtService.creditSummary(CUSTOMER_ID)).thenReturn(CustomerCreditSummaryResponse.builder()
                .customerId(CUSTOMER_ID).customerCode("KH-001").customerName("Khách POS")
                .customerStatus("ACTIVE").creditLimit(BigDecimal.TEN)
                .outstandingBalance(BigDecimal.ONE).overdueBalance(BigDecimal.ZERO)
                .availableCredit(BigDecimal.valueOf(9)).debtStatus(DebtStatus.WITHIN_TERM).build());

        mockMvc.perform(get("/api/v1/finance/credit-control/customers/{customerId}/summary", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerCode").value("KH-001"))
                .andExpect(jsonPath("$.data.invoices").doesNotExist())
                .andExpect(jsonPath("$.data.reminderHistory").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "TECHNICAL_STAFF")
    void technicalStaffCannotReadDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/finance/customer-debts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void creditCheckRejectsInvalidAmountBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/finance/customer-debts/{customerId}/credit-check", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderChannel\":\"COUNTER\",\"newOrderAmount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void onlyShopOwnerCanApproveCreditOverride() throws Exception {
        mockMvc.perform(post("/api/v1/finance/customer-debts/orders/{orderId}/credit-override", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Approved for this project\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanApproveCreditOverride() throws Exception {
        UUID actorId = UUID.fromString("10000000-0000-4000-8000-000000000003");
        when(iamApi.getCurrentUserId()).thenReturn(actorId);

        mockMvc.perform(post("/api/v1/finance/customer-debts/orders/{orderId}/credit-override", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Approved for this project\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(customerDebtService).approveOverride(eq(ORDER_ID), any(ReasonRequest.class), eq(actorId));
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanSendReminderAndRecordCallResult() throws Exception {
        UUID actorId = UUID.fromString("10000000-0000-4000-8000-000000000004");
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(customerDebtService.sendReminder(eq(CUSTOMER_ID), any(DebtReminderRequest.class), eq(actorId)))
                .thenReturn(new DebtReminderResponse(BigDecimal.TEN, "SENT", "NOT_CONFIGURED", "Dispatched"));

        mockMvc.perform(post("/api/v1/finance/customer-debts/{customerId}/reminders", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reminderType\":\"OVERDUE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.emailResult").value("SENT"));

        mockMvc.perform(post("/api/v1/finance/customer-debts/{customerId}/reminder-calls", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callResult\":\"PROMISED_TO_PAY\",\"promisedPaymentDate\":\"2026-12-01\",\"note\":\"Khách hẹn thanh toán\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(customerDebtService).sendReminder(eq(CUSTOMER_ID), any(DebtReminderRequest.class), eq(actorId));
        verify(customerDebtService).saveCallResult(eq(CUSTOMER_ID), any(DebtCallResultRequest.class), eq(actorId));
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffCanSendReminderAndRecordCallResult() throws Exception {
        UUID actorId = UUID.fromString("10000000-0000-4000-8000-000000000005");
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(customerDebtService.sendReminder(eq(CUSTOMER_ID), any(DebtReminderRequest.class), eq(actorId)))
                .thenReturn(new DebtReminderResponse(BigDecimal.TEN, "SENT", "NOT_CONFIGURED", "Dispatched"));

        mockMvc.perform(post("/api/v1/finance/customer-debts/{customerId}/reminders", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reminderType\":\"UPCOMING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/finance/customer-debts/{customerId}/reminder-calls", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callResult\":\"NO_ANSWER\",\"note\":\"Không nghe máy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(customerDebtService).sendReminder(eq(CUSTOMER_ID), any(DebtReminderRequest.class), eq(actorId));
        verify(customerDebtService).saveCallResult(eq(CUSTOMER_ID), any(DebtCallResultRequest.class), eq(actorId));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanSendReminderAndRecordCallResult() throws Exception {
        UUID actorId = UUID.fromString("10000000-0000-4000-8000-000000000006");
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(customerDebtService.sendReminder(eq(CUSTOMER_ID), any(DebtReminderRequest.class), eq(actorId)))
                .thenReturn(new DebtReminderResponse(BigDecimal.TEN, "SENT", "NOT_CONFIGURED", "Dispatched"));

        mockMvc.perform(post("/api/v1/finance/customer-debts/{customerId}/reminders", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reminderType\":\"FINAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/finance/customer-debts/{customerId}/reminder-calls", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callResult\":\"NEEDS_REVIEW\",\"note\":\"Cần rà soát đối chiếu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(customerDebtService).sendReminder(eq(CUSTOMER_ID), any(DebtReminderRequest.class), eq(actorId));
        verify(customerDebtService).saveCallResult(eq(CUSTOMER_ID), any(DebtCallResultRequest.class), eq(actorId));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerRoleCannotSendReminderOrRecordCallResult() throws Exception {
        mockMvc.perform(post("/api/v1/finance/customer-debts/{customerId}/reminders", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reminderType\":\"OVERDUE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/finance/customer-debts/{customerId}/reminder-calls", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callResult\":\"PROMISED_TO_PAY\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(customerDebtService);
    }

    @Test
    @WithMockUser(roles = "SHIPPER")
    void shipperRoleCannotSendReminderOrRecordCallResult() throws Exception {
        mockMvc.perform(post("/api/v1/finance/customer-debts/{customerId}/reminders", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reminderType\":\"OVERDUE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/finance/customer-debts/{customerId}/reminder-calls", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callResult\":\"NO_ANSWER\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(customerDebtService);
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCannotBlockOrRestoreCustomer() throws Exception {
        mockMvc.perform(patch("/api/v1/finance/customer-debts/{customerId}/block", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Overdue debt\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/finance/customer-debts/{customerId}/restore", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Paid in full\"}"))
                .andExpect(status().isForbidden());
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
