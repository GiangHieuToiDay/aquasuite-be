package com.aquasuite.ims.module.catalog.internal.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aquasuite.ims.module.catalog.internal.service.BundleAdminService;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BundleAdminControllerTest {
    MockMvc mvc;
    BundleAdminService service;

    @BeforeEach
    void setup() {
        service = mock(BundleAdminService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new BundleAdminController(service))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void bindingAndUnknownBody() {
        try {
            mvc.perform(get("/api/v1/catalog/admin/bundles/not-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SYS_002"));
            mvc.perform(
                            delete(
                                    "/api/v1/catalog/admin/bundles/10000000-0000-4000-8000-000000000001"))
                    .andExpect(status().isBadRequest());
            mvc.perform(
                            post("/api/v1/catalog/admin/bundles")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"productCode\":\"A\",\"unknownField\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SYS_003"));
            mvc.perform(
                            patch(
                                            "/api/v1/catalog/admin/bundles/10000000-0000-4000-8000-000000000001/status")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"expectedVersion\":0,\"productStatus\":\"DELETED\"}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(service);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/catalog/admin/bundles", "/api/v1/catalog/admin/bundle-skus"})
    void unknownOrRepeatedQueryParametersAreRejected(String path) throws Exception {
        mvc.perform(get(path).param("surprise", "value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));
        mvc.perform(get(path).param("page", "0", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));
        verifyNoInteractions(service);
    }
}
