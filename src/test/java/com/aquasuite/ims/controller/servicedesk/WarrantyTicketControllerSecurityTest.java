package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.response.ClaimFormResponse;
import com.aquasuite.ims.dto.servicedesk.response.StoredMedia;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketDetailResponse;
import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.service.servicedesk.WarrantyTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security & validation tests cho UC-20.2 (Customer Claim) & UC-21.5 (Shop Owner View Warranty Tickets).
 */
@WebMvcTest(
        controllers = WarrantyTicketController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.security\\..*"))
@Import({GlobalExceptionHandler.class, WarrantyTicketControllerSecurityTest.TestSecurityConfig.class})
class WarrantyTicketControllerSecurityTest {

    private static final UUID RECORD_ID = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final UUID TICKET_ID = UUID.fromString("60000000-0000-4000-8000-000000000002");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WarrantyTicketService warrantyTicketService;

    @Test
    void unauthenticatedRequests_return401() throws Exception {
        mockMvc.perform(get("/api/v1/warranty-tickets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/warranty-tickets/{id}", TICKET_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/storefront/warranty-records/{id}/claim-form", RECORD_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/storefront/warranty-tickets")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(warrantyTicketService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_canUseStorefrontClaimEndpoints_andIsForbiddenOnAdminTickets() throws Exception {
        when(warrantyTicketService.getClaimForm(RECORD_ID)).thenReturn(
                ClaimFormResponse.builder().warrantyRecordId(RECORD_ID).warrantyCode("WAR-01").canSubmit(true).build());
        when(warrantyTicketService.uploadMedia(any())).thenReturn(
                new StoredMedia("IMAGE", "https://cdn/img.jpg", "p1", "image/jpeg", 128));
        when(warrantyTicketService.submit(any(CreateWarrantyTicketRequest.class))).thenReturn(
                WarrantyTicketDetailResponse.builder().ticketId(TICKET_ID).ticketCode("TCK-WAR-001").status(WarrantyTicketStatus.NEW).build());

        mockMvc.perform(get("/api/v1/storefront/warranty-records/{id}/claim-form", RECORD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canSubmit").value(true));

        MockMultipartFile img = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});
        mockMvc.perform(multipart("/api/v1/storefront/warranty-tickets/media").file(img))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("IMAGE"));

        mockMvc.perform(post("/api/v1/storefront/warranty-tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"warrantyRecordId":"%s",
                                 "issueDescription":"Thiết bị bị rò nước liên tục tại khớp nối",
                                 "siteAddress":"15 Lê Duẩn, Q.1, TP.HCM"}
                                """, RECORD_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ticketCode").value("TCK-WAR-001"));

        mockMvc.perform(get("/api/v1/warranty-tickets")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/warranty-tickets/{id}", TICKET_ID)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwner_canListAndViewTickets_andIsForbiddenOnCustomerClaimEndpoints() throws Exception {
        when(warrantyTicketService.list(any())).thenReturn(PageResponse.of(List.of(), 0, 20, 0, 0));
        when(warrantyTicketService.getById(TICKET_ID)).thenReturn(
                WarrantyTicketDetailResponse.builder().ticketId(TICKET_ID).ticketCode("TCK-WAR-002").status(WarrantyTicketStatus.NEW).build());

        mockMvc.perform(get("/api/v1/warranty-tickets?status=NEW")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/warranty-tickets/{id}", TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketCode").value("TCK-WAR-002"));

        mockMvc.perform(get("/api/v1/storefront/warranty-records/{id}/claim-form", RECORD_ID))
                .andExpect(status().isForbidden());
        verify(warrantyTicketService).getById(TICKET_ID);
    }

    @Test
    @WithMockUser(roles = {"SALES_STAFF", "ACCOUNTANT", "TECHNICIAN"})
    void otherStaffRoles_areForbiddenOnBothAdminTicketsAndCustomerClaim() throws Exception {
        mockMvc.perform(get("/api/v1/warranty-tickets")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/warranty-tickets/{id}", TICKET_ID)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/storefront/warranty-records/{id}/claim-form", RECORD_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(warrantyTicketService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void submitTicket_invalidFields_returns400Sys003() throws Exception {
        mockMvc.perform(post("/api/v1/storefront/warranty-tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"warrantyRecordId":"%s",
                                 "issueDescription":"Ngắn",
                                 "preferredDate":"2020-01-01",
                                 "siteAddress":"  "}
                                """, RECORD_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        verifyNoInteractions(warrantyTicketService);
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
