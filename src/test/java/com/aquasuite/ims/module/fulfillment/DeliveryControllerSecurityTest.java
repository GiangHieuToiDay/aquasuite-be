package com.aquasuite.ims.module.fulfillment;

import com.aquasuite.ims.module.fulfillment.internal.controller.*;
import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryService;
import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryIssueNotePdfService;
import com.aquasuite.ims.share.exception.*;
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
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.Page;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(DeliveryControllerSecurityTest.Config.class)
class DeliveryControllerSecurityTest {
    @Configuration
    @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config implements WebMvcConfigurer {
        @Bean DeliveryService service() { return mock(DeliveryService.class); }
        @Bean DeliveryIssueNotePdfService pdfService() { return mock(DeliveryIssueNotePdfService.class); }
        @Bean DeliveryIssueNoteController issueNotes(DeliveryIssueNotePdfService s) { return new DeliveryIssueNoteController(s); }
        @Bean DeliveryController deliveries(DeliveryService s) { return new DeliveryController(s); }
        @Bean ShipperDeliveryController shipper(DeliveryService s) { return new ShipperDeliveryController(s); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .httpBasic(org.springframework.security.config.Customizer.withDefaults()).build();
        }
        @Override public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new PageableHandlerMethodArgumentResolver());
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired DeliveryService service;
    @Autowired DeliveryIssueNotePdfService pdfService;
    MockMvc mvc;
    String id = UUID.randomUUID().toString();
    @BeforeEach void setup() {
        reset(service);
        reset(pdfService);
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test @WithMockUser(roles = "SHOP_OWNER") void ownerCanListDeliveries() throws Exception {
        mvc.perform(get("/api/v1/fulfillment/deliveries")).andExpect(status().isOk());
    }
    @Test @WithMockUser(roles = "ACCOUNTANT") void accountantCanDownloadIssueNote() throws Exception {
        when(pdfService.generate(any())).thenReturn("%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        mvc.perform(get("/api/v1/fulfillment/deliveries/"+id+"/issue-note.pdf"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test @WithMockUser(roles = "SHIPPER") void shipperReachesObjectAuthorizationForPdf() throws Exception {
        mvc.perform(get("/api/v1/fulfillment/deliveries/"+id+"/issue-note.pdf")).andExpect(status().isOk());
        verify(pdfService).generate(UUID.fromString(id));
    }
    @Test @WithMockUser(roles = "SALES_STAFF") void salesCannotDownloadIssueNote() throws Exception {
        mvc.perform(get("/api/v1/fulfillment/deliveries/"+id+"/issue-note.pdf")).andExpect(status().isForbidden());
        verifyNoInteractions(pdfService);
    }
    @Test @WithMockUser(roles="SHIPPER") void shipperCanRequestReschedule() throws Exception {
        mvc.perform(post("/api/v1/fulfillment/deliveries/"+id+"/reschedule").contentType("application/json")
                .content("{\"scheduledDate\":\"2099-01-01T09:00:00+07:00\",\"reason\":\"Customer requested another date\"}"))
                .andExpect(status().isOk());
        verify(service).reschedule(eq(UUID.fromString(id)),any());
    }
    @Test @WithMockUser(roles="SHIPPER") void failRequiresExplicitCustomerCancellationConfirmation() throws Exception {
        for(String body:List.of("{\"reason\":\"No contact\"}","{\"reason\":\"No contact\",\"customerCancellationConfirmed\":false}")) {
            mvc.perform(post("/api/v1/fulfillment/deliveries/"+id+"/fail").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="ACCOUNTANT") void accountantCannotReportShipperReschedule() throws Exception {
        mvc.perform(post("/api/v1/fulfillment/deliveries/"+id+"/reschedule").contentType("application/json")
                .content("{\"scheduledDate\":\"2099-01-01T09:00:00+07:00\",\"reason\":\"Customer requested another date\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles = "SHOP_OWNER") void ownerCannotMarkDelivered() throws Exception {
        mvc.perform(multipart("/api/v1/fulfillment/deliveries/" + id + "/complete")
            .file(new MockMultipartFile("photos", "a.png", "image/png", new byte[]{1})))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void anonymousDenied() throws Exception {
        mvc.perform(get("/api/v1/fulfillment/deliveries")).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser(roles = "SHIPPER") void shipperCannotManageAssignments() throws Exception {
        mvc.perform(put("/api/v1/fulfillment/delivery-assignments").contentType("application/json")
            .content("{\"shipperId\":\"" + id + "\",\"deliveryIds\":[\"" + id + "\"]}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles = "ACCOUNTANT") void accountantCannotMarkDelivered() throws Exception {
        mvc.perform(multipart("/api/v1/fulfillment/deliveries/" + id + "/complete")
            .file(new MockMultipartFile("photos", "a.png", "image/png", new byte[]{1})))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles = "SALES_STAFF") void otherRolesCannotReadDetails() throws Exception {
        mvc.perform(get("/api/v1/fulfillment/deliveries/" + id)).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles = "ACCOUNTANT") void emptyAssignmentRejected() throws Exception {
        mvc.perform(put("/api/v1/fulfillment/delivery-assignments").contentType("application/json")
            .content("{\"shipperId\":\"" + id + "\",\"deliveryIds\":[]}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles = "SHIPPER") void blankFailureRejected() throws Exception {
        mvc.perform(post("/api/v1/fulfillment/deliveries/" + id + "/fail").contentType("application/json")
            .content("{\"reason\":\" \"}" )).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles = "SHIPPER") void completionMapsStorageUnavailableTo503() throws Exception {
        when(service.complete(any(), any())).thenThrow(new AppException(
            com.aquasuite.ims.module.fulfillment.internal.error.FulfillmentErrorCode.STORAGE_UNAVAILABLE));
        mvc.perform(multipart("/api/v1/fulfillment/deliveries/" + id + "/complete")
            .file(new MockMultipartFile("photos", "a.png", "image/png", new byte[]{1})))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("FUL_004"));
    }
    @Test @WithMockUser(roles = "ACCOUNTANT") void accountantCanList() throws Exception {
        when(service.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty(org.springframework.data.domain.PageRequest.of(0, 20)));
        mvc.perform(get("/api/v1/fulfillment/deliveries")).andExpect(status().isOk());
    }
}
