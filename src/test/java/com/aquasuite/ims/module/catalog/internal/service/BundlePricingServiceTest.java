package com.aquasuite.ims.module.catalog.internal.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

class BundlePricingServiceTest {
    private final BundlePricingService pricing = new BundlePricingService();

    @Test
    void srsExample() {
        var p =
                pricing.preview(
                        List.of(new BundlePricingService.Line(new BigDecimal("110000000"), 1)),
                        new BigDecimal("95000000"));
        assertThat(p.savingAmount()).isEqualByComparingTo("15000000");
        assertThat(p.discountPercent()).isEqualByComparingTo("13.6");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "110000001", "1.001", "10000000000000"})
    void invalidPrice(String value) {
        assertThatThrownBy(
                        () ->
                                pricing.validate(
                                        List.of(
                                                new BundlePricingService.Line(
                                                        new BigDecimal("110000000"), 1)),
                                        new BigDecimal(value)))
                .isInstanceOf(com.aquasuite.ims.share.exception.AppException.class);
    }

    @Test
    void retailOverflow() {
        var lines = List.of(new BundlePricingService.Line(BundlePricingService.MAX, 10000));
        assertThat(pricing.preview(lines, BigDecimal.ONE).priceIssue())
                .isEqualTo("INVALID_RETAIL_TOTAL");
        assertThatThrownBy(() -> pricing.validate(lines, BigDecimal.ONE))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void priceDropAndMissingPrice() {
        assertThat(
                        pricing.preview(
                                        List.of(new BundlePricingService.Line(BigDecimal.ONE, 1)),
                                        BigDecimal.TEN)
                                .savingAmount())
                .isNull();
        assertThat(
                        pricing.preview(
                                        List.of(new BundlePricingService.Line(null, 1)),
                                        BigDecimal.ONE)
                                .originalTotal())
                .isNull();
    }
}
