package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.module.catalog.internal.payload.req.CreateBundleRequest;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

class BundleTimestampTest {
    final com.fasterxml.jackson.databind.ObjectMapper json =
            JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @ParameterizedTest
    @ValueSource(strings = {"\"2026-11-01T00:00:00\"", "\"invalid\""})
    void timestampsRequireExplicitOffset(String value) {
        assertThatThrownBy(
                        () ->
                                json.readValue(
                                        "{\"validFrom\":" + value + "}", CreateBundleRequest.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    @Test
    void offsetsNormalizeToUtc() throws Exception {
        var request =
                json.readValue(
                        "{\"validFrom\":\"2026-11-01T07:00:00+07:00\"}", CreateBundleRequest.class);
        assertThat(request.validFrom()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    }
}
