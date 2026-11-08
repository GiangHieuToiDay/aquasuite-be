package com.aquasuite.ims.module.catalog.internal.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aquasuite.ims.module.catalog.internal.service.*;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BundlePublicationControllerTest {
    final BundleAdminService service = mock(BundleAdminService.class);
    final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(
                            new BundleAdminController(service, mock(BundleQueryService.class)))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
    final String url =
            "/api/v1/catalog/bundle/admin/10000000-0000-4000-8000-000000000001/publication-status";

    @ParameterizedTest
    @ValueSource(strings = {"true", "\"DRAFT\"", "\"published\"", "null"})
    void invalidPublicationCannotBeCoerced(String value) throws Exception {
        mvc.perform(
                        patch(url)
                                .contentType("application/json")
                                .content(
                                        "{\"publicationStatus\":"
                                                + value
                                                + ",\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void missingOrNegativeVersionRejected() throws Exception {
        for (String body :
                new String[] {
                    "{\"publicationStatus\":\"PUBLISHED\"}",
                    "{\"publicationStatus\":\"PUBLISHED\",\"expectedVersion\":-1}"
                }) {
            mvc.perform(patch(url).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
