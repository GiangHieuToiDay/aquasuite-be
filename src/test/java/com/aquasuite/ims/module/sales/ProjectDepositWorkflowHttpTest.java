package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.finance.internal.dev.*;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.sales.internal.dev.DemoProjectReservationOrderFactory;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.internal.dev.*;
import com.aquasuite.ims.module.sales.internal.controller.ProjectDepositController;
import com.aquasuite.ims.module.sales.internal.dev.ProjectDepositDemoConfiguration;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.repository.*;
import com.aquasuite.ims.module.sales.internal.service.ProjectDepositService;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectDepositWorkflowHttpTest {
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void realControllersOrchestrateFiniteStockPendingDepositVerificationAndPromotion() {
        UUID orderId = UUID.randomUUID(), variantId = UUID.randomUUID(), actor = UUID.randomUUID(), lineId = UUID.randomUUID();
        var orders = mock(ProjectDepositOrderRepository.class);
        var items = mock(ProjectDepositOrderItemRepository.class);
        var iam = mock(IamApi.class);
        var catalog = mock(CatalogApi.class);
        var crm = mock(CrmApi.class);
        UUID customerId = UUID.randomUUID();
        when(catalog.getSkuSummary(variantId)).thenReturn(Optional.of(CatalogSkuSummaryDto.builder().variantId(variantId)
                .sellingPrice(BigDecimal.TEN).unit("piece").status("ACTIVE").build()));
        when(crm.getCustomerById(customerId)).thenReturn(Optional.of(CustomerSummaryDto.builder().customerId(customerId).status("ACTIVE").build()));
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SHOP_OWNER")).build());
        when(iam.getCurrentUserId()).thenReturn(actor);
        when(orders.findById(orderId)).thenReturn(Optional.of(Order.builder().orderId(orderId)
                .customerId(UUID.randomUUID()).orderStatus("PROCESSING").totalAmount(new BigDecimal("100")).build()));
        when(items.findByOrderIdOrderByOrderItemId(orderId)).thenReturn(List.of(OrderItem.builder()
                .orderItemId(lineId).orderId(orderId).variantId(variantId).quantity(BigDecimal.TEN).lineTotal(new BigDecimal("100")).build()));
        new ApplicationContextRunner().withPropertyValues("spring.profiles.active=uc29-demo")
                .withUserConfiguration(ProjectDepositDemoConfiguration.class, Security.class,
                        ProjectDepositService.class, ProjectDepositController.class, DemoProjectReservationOrderFactory.class, DemoProjectDepositPaymentApi.class,
                        DemoProjectDepositController.class, DemoProjectReservationApi.class, DemoProjectStockController.class)
                .withBean(CatalogApi.class, () -> catalog).withBean(CrmApi.class, () -> crm)
                .withBean(IamApi.class, () -> iam).withBean(ProjectDepositOrderRepository.class, () -> orders)
                .withBean(ProjectDepositOrderItemRepository.class, () -> items).run(context -> {
                    assertThat(context).hasNotFailed();
                    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            "demo-owner", null, List.of(new SimpleGrantedAuthority("ROLE_SHOP_OWNER"))));
                    var mvc = MockMvcBuilders.standaloneSetup(context.getBean(ProjectDepositController.class),
                                    context.getBean(DemoProjectDepositController.class), context.getBean(DemoProjectStockController.class))
                            .setControllerAdvice(new GlobalExceptionHandler()).build();
                    String prefix = "/api/v1/sales/orders/"+orderId+"/deposits-reservations";
                    mvc.perform(post("/api/v1/dev/uc29/inventory-stock").contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"warehouseCode\":\"WH_MAIN\",\"variantId\":\""+variantId+"\",\"onHandQty\":6}"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.data.simulated").value(true));
                    String temporary = mvc.perform(post(prefix+"/requests").contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"requestId\":\""+UUID.randomUUID()+"\",\"warehouseCode\":\"WH_MAIN\",\"lines\":[{\"orderItemId\":\""+lineId+"\",\"quantity\":10}]}"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.data.lines[0].heldQty").value(0))
                            .andExpect(jsonPath("$.data.lines[0].waitingQty").value(10)).andReturn().getResponse().getContentAsString();
                    String deposit = mvc.perform(post(prefix+"/deposits").contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"requestId\":\""+UUID.randomUUID()+"\",\"amount\":30,\"method\":\"CASH\",\"warehouseCode\":\"WH_MAIN\"}"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.data.verifiedPaidAmount").value(0))
                            .andExpect(jsonPath("$.data.payments[0].status").value("PENDING")).andReturn().getResponse().getContentAsString();
                    ObjectMapper json = new ObjectMapper();
                    String paymentId = json.readTree(deposit).at("/data/payments/0/paymentId").asText();
                    mvc.perform(post("/api/v1/dev/uc29/payments/"+paymentId+"/verification").contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"status\":\"VERIFIED\"}"))
                            .andExpect(status().isOk());
                    mvc.perform(post(prefix+"/synchronize").contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"requestId\":\""+UUID.randomUUID()+"\",\"warehouseCode\":\"WH_MAIN\"}"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.data.simulated").value(true))
                            .andExpect(jsonPath("$.data.outstandingAmount").value(70))
                            .andExpect(jsonPath("$.data.reservations[0].type").value("DEPOSIT_BACKED"))
                            .andExpect(jsonPath("$.data.lines[0].heldQty").value(6))
                            .andExpect(jsonPath("$.data.lines[0].waitingQty").value(4));
                    String createBody = "{\"requestId\":\""+UUID.randomUUID()+"\",\"customerId\":\""+customerId
                            +"\",\"lines\":[{\"variantId\":\""+variantId+"\",\"quantity\":10}],\"amount\":20,\"method\":\"CASH\",\"warehouseCode\":\"WH_MAIN\"}";
                    String created = mvc.perform(post("/api/v1/sales/deposit-reservation-orders").contentType(MediaType.APPLICATION_JSON).content(createBody))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.data.requiredDeposit").value(20))
                            .andExpect(jsonPath("$.data.reservationRequested").value(true))
                            .andExpect(jsonPath("$.data.depositSatisfied").value(false))
                            .andExpect(jsonPath("$.data.payments[0].status").value("PENDING"))
                            .andReturn().getResponse().getContentAsString();
                    String newOrderId = json.readTree(created).at("/data/orderId").asText();
                    assertThat(newOrderId).isNotEqualTo(orderId.toString());
                    mvc.perform(post("/api/v1/sales/deposit-reservation-orders").contentType(MediaType.APPLICATION_JSON).content(createBody))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.data.orderId").value(newOrderId))
                            .andExpect(jsonPath("$.data.payments.length()").value(1));
                    verify(orders, never()).save(any());
                    verify(items, never()).save(any());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Security {}
}
