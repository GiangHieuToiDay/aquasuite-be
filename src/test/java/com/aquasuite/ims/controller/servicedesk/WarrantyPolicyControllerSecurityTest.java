package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.enums.servicedesk.WarrantyPolicyStatus;
import com.aquasuite.ims.dto.servicedesk.request.UpsertWarrantyPolicyRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicyDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicyReferenceDataResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicyResponse;
import com.aquasuite.ims.service.servicedesk.WarrantyPolicyService;
import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WarrantyPolicyController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.security\\..*"))
@Import({GlobalExceptionHandler.class, WarrantyPolicyControllerSecurityTest.TestSecurityConfig.class})
class WarrantyPolicyControllerSecurityTest {

    private static final UUID POLICY_ID = UUID.fromString("10000000-0000-4000-8000-000000000044");
    private static final UUID BRAND_ID = UUID.fromString("10000000-0000-4000-8000-000000000045");
    private static final UUID CATEGORY_ID = UUID.fromString("10000000-0000-4000-8000-000000000046");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarrantyPolicyService warrantyPolicyService;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/warranty-policies"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/warranty-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpsertJson()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(warrantyPolicyService);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void shopOwnerCanOperateAllCrudAndLifecycleEndpoints() throws Exception {
        WarrantyPolicyResponse policyResponse = samplePolicyResponse(WarrantyPolicyStatus.ACTIVE);
        when(warrantyPolicyService.search(eq("TOTO"), eq(WarrantyPolicyStatus.ACTIVE), any()))
                .thenReturn(PageResponse.of(List.of(policyResponse), 0, 20, 1, 1));
        when(warrantyPolicyService.referenceData())
                .thenReturn(WarrantyPolicyReferenceDataResponse.builder().brands(List.of()).categories(List.of()).build());
        when(warrantyPolicyService.detail(POLICY_ID))
                .thenReturn(WarrantyPolicyDetailResponse.builder().policy(policyResponse).variants(List.of()).build());
        when(warrantyPolicyService.create(any(UpsertWarrantyPolicyRequest.class)))
                .thenReturn(policyResponse);
        when(warrantyPolicyService.update(eq(POLICY_ID), any(UpsertWarrantyPolicyRequest.class)))
                .thenReturn(policyResponse);
        when(warrantyPolicyService.deactivate(POLICY_ID))
                .thenReturn(samplePolicyResponse(WarrantyPolicyStatus.INACTIVE));

        mockMvc.perform(get("/api/v1/warranty-policies?keyword=TOTO&status=ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].policyCode").value("POL-00001"));

        mockMvc.perform(get("/api/v1/warranty-policies/reference-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/warranty-policies/{policyId}", POLICY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policy.policyId").value(POLICY_ID.toString()));

        mockMvc.perform(post("/api/v1/warranty-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpsertJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.policyCode").value("POL-00001"));

        mockMvc.perform(put("/api/v1/warranty-policies/{policyId}", POLICY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpsertJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policyCode").value("POL-00001"));

        mockMvc.perform(post("/api/v1/warranty-policies/{policyId}/deactivate", POLICY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(delete("/api/v1/warranty-policies/{policyId}", POLICY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(warrantyPolicyService).delete(POLICY_ID);
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void salesStaffIsForbiddenAcrossAllWarrantyPolicyEndpoints() throws Exception {
        assertAllEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantIsForbiddenAcrossAllWarrantyPolicyEndpoints() throws Exception {
        assertAllEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerIsForbiddenAcrossAllWarrantyPolicyEndpoints() throws Exception {
        assertAllEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "TECHNICAL_STAFF")
    void technicalStaffIsForbiddenAcrossAllWarrantyPolicyEndpoints() throws Exception {
        assertAllEndpointsForbidden();
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void createAndUpdateValidatePayloadBeforeInvokingService() throws Exception {
        String invalidBody = """
                {
                  "policyName": "   ",
                  "brandId": null,
                  "categoryId": null,
                  "warrantyPeriodMonths": 0,
                  "coverageConditions": "",
                  "status": null
                }
                """;

        mockMvc.perform(post("/api/v1/warranty-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"))
                .andExpect(jsonPath("$.errors").isArray());

        mockMvc.perform(put("/api/v1/warranty-policies/{policyId}", POLICY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyName": "Chính sách TOTO",
                                  "brandId": "%s",
                                  "categoryId": "%s",
                                  "warrantyPeriodMonths": -6,
                                  "coverageConditions": "Bảo hành chính hãng",
                                  "status": "ACTIVE"
                                }
                                """.formatted(BRAND_ID, CATEGORY_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        verifyNoInteractions(warrantyPolicyService);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void createRejectsWarrantyPeriodOver600Months() throws Exception {
        String overMaxBody = """
                {
                  "policyName": "Chính sách TOTO",
                  "brandId": "%s",
                  "categoryId": "%s",
                  "warrantyPeriodMonths": 601,
                  "coverageConditions": "Bảo hành chính hãng",
                  "status": "ACTIVE"
                }
                """.formatted(BRAND_ID, CATEGORY_ID);

        mockMvc.perform(post("/api/v1/warranty-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overMaxBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        verifyNoInteractions(warrantyPolicyService);
    }

    private void assertAllEndpointsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/warranty-policies"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        mockMvc.perform(get("/api/v1/warranty-policies/reference-data"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/warranty-policies/{policyId}", POLICY_ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/warranty-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpsertJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/warranty-policies/{policyId}", POLICY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpsertJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/warranty-policies/{policyId}/deactivate", POLICY_ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/warranty-policies/{policyId}", POLICY_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(warrantyPolicyService);
    }

    private String validUpsertJson() {
        return """
                {
                  "policyName": "Bảo hành sứ vệ sinh TOTO",
                  "brandId": "%s",
                  "categoryId": "%s",
                  "warrantyPeriodMonths": 120,
                  "coverageConditions": "Bảo hành 10 năm phần sứ vệ sinh chính hãng.",
                  "status": "ACTIVE"
                }
                """.formatted(BRAND_ID, CATEGORY_ID);
    }

    private WarrantyPolicyResponse samplePolicyResponse(WarrantyPolicyStatus status) {
        return WarrantyPolicyResponse.builder()
                .policyId(POLICY_ID)
                .policyCode("POL-00001")
                .policyName("Bảo hành sứ vệ sinh TOTO")
                .brandId(BRAND_ID)
                .brandName("TOTO")
                .categoryId(CATEGORY_ID)
                .categoryName("Bồn cầu")
                .warrantyPeriodMonths(120)
                .coverageConditions("Bảo hành 10 năm phần sứ vệ sinh chính hãng.")
                .status(status)
                .variantReferenceCount(0L)
                .build();
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
