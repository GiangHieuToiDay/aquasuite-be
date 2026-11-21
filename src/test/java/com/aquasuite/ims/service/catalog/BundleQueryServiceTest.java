package com.aquasuite.ims.service.catalog;
import com.aquasuite.ims.service.catalog.helper.BundleDetailAssembler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aquasuite.ims.entity.catalog.BundleConfiguration;
import com.aquasuite.ims.mapper.catalog.BundleMapper;
import com.aquasuite.ims.dto.catalog.response.BundleResponse;
import com.aquasuite.ims.repository.catalog.*;
import com.aquasuite.ims.service.catalog.impl.BundleQueryServiceImpl;
import com.aquasuite.ims.exception.AppException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.*;
import java.util.*;

class BundleQueryServiceTest {
    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "1,true", "59,true", "60,false", "61,false"})
    void visibilityUsesInclusiveStartExclusiveEnd(long offset, boolean visible) {
        UUID id = UUID.randomUUID();
        Instant from = Instant.parse("2026-10-20T00:00:00Z");
        var configs = mock(BundleConfigurationRepository.class);
        var repo = mock(BundleRepository.class);
        var config = new BundleConfiguration();
        config.setPublicationStatus(BundleConfiguration.PublicationStatus.PUBLISHED);
        config.setValidFrom(from);
        config.setValidUntil(from.plusSeconds(60));
        when(configs.findById(id)).thenReturn(Optional.of(config));
        var detail =
                new BundleResponse(
                        id,
                        "CODE",
                        "Name",
                        "ACTIVE",
                        "PUBLISHED",
                        0L,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        from,
                        from,
                        null,
                        null,
                        null,
                        List.of(),
                        from,
                        from.plusSeconds(60));
        var product = new com.aquasuite.ims.entity.catalog.Product();
        product.setProductId(id);
        var assembler = mock(BundleDetailAssembler.class);
        when(repo.findBundleProduct(id, false)).thenReturn(product);
        when(repo.loadAggregates(List.of(product)))
                .thenReturn(
                        Map.of(
                                id,
                                new BundleRepository.BundleData(
                                        product, config, List.of(), Map.of(), Map.of())));
        when(assembler.detail(product, config, List.of(), Map.of(), Map.of())).thenReturn(detail);
        var query =
                new BundleQueryServiceImpl(
                        mock(ProductRepository.class),
                        mock(ProductVariantRepository.class),
                        configs,
                        repo,
                        mock(BundleMapper.class),
                        assembler,
                        Clock.fixed(from.plusSeconds(offset), ZoneOffset.UTC));
        if (visible) assertThat(query.getPublishedBundleDetails(id)).isSameAs(detail);
        else
            assertThatThrownBy(() -> query.getPublishedBundleDetails(id))
                    .isInstanceOf(AppException.class);
    }
}
