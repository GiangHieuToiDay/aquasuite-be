package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.finance.api.ProjectDepositPaymentApi.Status;
import com.aquasuite.ims.module.finance.internal.dev.*;
import com.aquasuite.ims.module.inventory.internal.dev.*;
import com.aquasuite.ims.module.sales.internal.controller.ProjectDepositController;
import com.aquasuite.ims.module.sales.internal.service.ProjectDepositService;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectDepositHttpTest {
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void invalidAmountsAndMissingRequestIdsAreRejectedBeforeCallingService() throws Exception {
        var service = mock(ProjectDepositService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ProjectDepositController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String path = "/api/v1/sales/orders/"+UUID.randomUUID()+"/deposits-reservations";
        mvc.perform(post(path+"/deposits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1,\"method\":\"CASH\",\"warehouseCode\":\"WH_MAIN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS_003"));
        mvc.perform(post(path+"/requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\""+UUID.randomUUID()+"\",\"warehouseCode\":\"../WH\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sales/orders/not-a-uuid/deposits-reservations"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS_002"));
        verifyNoInteractions(service);
    }

    @Test
    void demoVerificationRequiresAccountantOrOwnerAndStockFixtureRequiresOwner() {
        var payments = mock(DemoProjectDepositPaymentApi.class);
        var inventory = mock(DemoProjectReservationApi.class);
        new ApplicationContextRunner().withUserConfiguration(MethodSecurity.class)
                .withPropertyValues("spring.profiles.active=uc29-demo")
                .withBean(DemoProjectDepositController.class, () -> new DemoProjectDepositController(payments))
                .withBean(DemoProjectStockController.class, () -> new DemoProjectStockController(inventory))
                .run(context -> {
                    var finance = context.getBean(DemoProjectDepositController.class);
                    var stock = context.getBean(DemoProjectStockController.class);
                    UUID id = UUID.randomUUID();
                    authenticate("ROLE_SALES_STAFF");
                    assertThatThrownBy(() -> finance.verify(id, new DemoProjectDepositController.Verification(Status.VERIFIED)))
                            .isInstanceOf(AccessDeniedException.class);
                    assertThatThrownBy(() -> stock.seed(new DemoProjectStockController.Stock("WH_MAIN", id, java.math.BigDecimal.TEN)))
                            .isInstanceOf(AccessDeniedException.class);
                    verifyNoInteractions(payments, inventory);
                    authenticate("ROLE_ACCOUNTANT");
                    finance.verify(id, new DemoProjectDepositController.Verification(Status.VERIFIED));
                    verify(payments).verify(id, Status.VERIFIED);
                    authenticate("ROLE_SHOP_OWNER");
                    stock.seed(new DemoProjectStockController.Stock("WH_MAIN", id, java.math.BigDecimal.TEN));
                    verify(inventory).setStock("WH_MAIN", id, java.math.BigDecimal.TEN);
                });
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "demo-user", null, List.of(new SimpleGrantedAuthority(authority))));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}
}
