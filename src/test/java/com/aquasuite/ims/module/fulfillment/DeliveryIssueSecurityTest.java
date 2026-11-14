package com.aquasuite.ims.module.fulfillment;

import com.aquasuite.ims.module.fulfillment.internal.controller.DeliveryIssueController;
import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryIssueService;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(DeliveryIssueSecurityTest.Config.class)
class DeliveryIssueSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean DeliveryIssueService service() { return mock(DeliveryIssueService.class); }
        @Bean DeliveryIssueController controller(DeliveryIssueService service) { return new DeliveryIssueController(service); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .httpBasic(org.springframework.security.config.Customizer.withDefaults()).build();
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired DeliveryIssueService service;
    MockMvc mvc;
    String id=UUID.randomUUID().toString();
    @BeforeEach void setup() {
        reset(service);
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test @WithMockUser(roles="ACCOUNTANT") void accountantCanConfirm() throws Exception {
        mvc.perform(post("/api/v1/fulfillment/deliveries/"+id+"/confirm-issue")).andExpect(status().isOk());
        verify(service).confirm(UUID.fromString(id));
    }
    @Test @WithMockUser(roles="ACCOUNTANT") void accountantCanListIssuedInvoices() throws Exception {
        mvc.perform(get("/api/v1/fulfillment/orders/"+id+"/delivery-invoices")).andExpect(status().isOk());
        verify(service).issuedInvoices(UUID.fromString(id));
    }
    @Test @WithMockUser(roles="SHIPPER") void shipperCannotListInvoicePicker() throws Exception {
        mvc.perform(get("/api/v1/fulfillment/orders/"+id+"/delivery-invoices")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="SHOP_OWNER") void ownerCanCancelDraft() throws Exception {
        mvc.perform(post("/api/v1/fulfillment/deliveries/"+id+"/cancel-draft")).andExpect(status().isOk());
    }
    @Test @WithMockUser(roles="SHIPPER") void shipperCannotIssue() throws Exception {
        mvc.perform(post("/api/v1/fulfillment/deliveries/"+id+"/confirm-issue")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="SALES_STAFF") void salesCannotIssue() throws Exception {
        mvc.perform(post("/api/v1/fulfillment/deliveries/"+id+"/confirm-issue")).andExpect(status().isForbidden());
    }
    @Test void anonymousCannotReadCandidates() throws Exception {
        mvc.perform(get("/api/v1/fulfillment/orders/"+id+"/delivery-candidates")).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser(roles="ACCOUNTANT") void invalidBodyRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/v1/fulfillment/orders/"+id+"/deliveries").contentType("application/json")
                .content("{\"deliveryId\":\""+id+"\",\"items\":[]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
