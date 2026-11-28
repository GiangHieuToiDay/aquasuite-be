package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyRecordFilterRequest;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.service.servicedesk.WarrantyAutoIssuanceService;
import com.aquasuite.ims.service.servicedesk.WarrantyRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WarrantyRecordController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.security\\..*"))
@Import({GlobalExceptionHandler.class, WarrantyRecordControllerAutoSyncTest.TestSecurityConfig.class})
class WarrantyRecordControllerAutoSyncTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarrantyRecordService warrantyRecordService;

    @MockitoBean
    private WarrantyAutoIssuanceService warrantyAutoIssuanceService;

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void searchRecordsTriggersAutoSyncAndContinuesIfSyncFails() throws Exception {
        when(warrantyAutoIssuanceService.syncEligibleRecords())
                .thenThrow(new RuntimeException("Temporary sync failure"));
        when(warrantyRecordService.searchRecords(any(WarrantyRecordFilterRequest.class)))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/v1/warranty-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(warrantyAutoIssuanceService).syncEligibleRecords();
        verify(warrantyRecordService).searchRecords(any(WarrantyRecordFilterRequest.class));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerEndpointsTriggerAutoSyncBeforeListing() throws Exception {
        when(warrantyRecordService.getMyRecords(any(WarrantyRecordFilterRequest.class)))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0L, 0));
        when(warrantyRecordService.findMine("TOTO")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/storefront/warranty-records"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customer/warranties").param("keyword", "TOTO"))
                .andExpect(status().isOk());

        verify(warrantyAutoIssuanceService, times(2)).syncEligibleRecords();
        verify(warrantyRecordService).getMyRecords(any(WarrantyRecordFilterRequest.class));
        verify(warrantyRecordService).findMine("TOTO");
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, authEx) -> res.sendError(401)))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }
    }
}
