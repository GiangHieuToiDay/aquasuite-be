package com.aquasuite.ims.module.servicedesk.internal.service;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderItemSummaryDto;
import com.aquasuite.ims.module.servicedesk.internal.entity.WarrantyRecord;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyLookupResponse;
import com.aquasuite.ims.module.servicedesk.internal.repository.WarrantyRecordRepository;
import com.aquasuite.ims.module.servicedesk.internal.service.impl.WarrantyLookupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarrantyLookupServiceImplTest {
    private final WarrantyRecordRepository warrantyRecords = mock(WarrantyRecordRepository.class);
    private final IamApi iamApi = mock(IamApi.class);
    private final CrmApi crmApi = mock(CrmApi.class);
    private final CatalogApi catalogApi = mock(CatalogApi.class);
    private final SalesOrderApi salesOrderApi = mock(SalesOrderApi.class);
    private WarrantyLookupService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC);
        service = new WarrantyLookupServiceImpl(warrantyRecords, iamApi, crmApi, catalogApi, salesOrderApi, clock);
    }

    @Test
    void returnsOnlyCurrentCustomersRecordsAndFiltersBySerial() {
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        WarrantyRecord record = record(customerId, variantId, orderItemId, "SN-CS886-260101", LocalDate.of(2026, 11, 13), LocalDate.of(2036, 11, 13));
        when(iamApi.getCurrentUserId()).thenReturn(userId);
        when(crmApi.getCustomerByUserId(userId)).thenReturn(Optional.of(CustomerSummaryDto.builder().customerId(customerId).build()));
        when(warrantyRecords.findByCustomerIdOrderByStartDateDesc(customerId)).thenReturn(List.of(record));
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(CatalogSkuSummaryDto.builder().productName("Bồn cầu TOTO").variantName("CS886").sku("CS886").build()));
        when(salesOrderApi.getOrderItemSummary(orderItemId)).thenReturn(Optional.of(SalesOrderItemSummaryDto.builder().orderCode("ORD-202611-001").build()));

        List<WarrantyLookupResponse> result = service.findMine("cs886-2601");

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.productName()).isEqualTo("Bồn cầu TOTO");
            assertThat(response.sku()).isEqualTo("CS886");
            assertThat(response.orderCode()).isEqualTo("ORD-202611-001");
            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.remainingDays()).isPositive();
            assertThat(response.claimAllowed()).isTrue();
        });
    }

    @Test
    void marksExpiredRecordAndNeverAllowsClaim() {
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        WarrantyRecord record = record(customerId, variantId, orderItemId, "SN-OLD", LocalDate.of(2023, 1, 1), LocalDate.of(2026, 11, 14));
        when(iamApi.getCurrentUserId()).thenReturn(userId);
        when(crmApi.getCustomerByUserId(userId)).thenReturn(Optional.of(CustomerSummaryDto.builder().customerId(customerId).build()));
        when(warrantyRecords.findByCustomerIdOrderByStartDateDesc(customerId)).thenReturn(List.of(record));
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.empty());
        when(salesOrderApi.getOrderItemSummary(orderItemId)).thenReturn(Optional.empty());

        WarrantyLookupResponse response = service.findMine(null).getFirst();

        assertThat(response.status()).isEqualTo("EXPIRED");
        assertThat(response.remainingDays()).isZero();
        assertThat(response.claimAllowed()).isFalse();
    }

    private WarrantyRecord record(UUID customerId, UUID variantId, UUID orderItemId, String serial, LocalDate start, LocalDate end) {
        return WarrantyRecord.builder().warrantyRecordId(UUID.randomUUID()).customerId(customerId).variantId(variantId)
                .orderItemId(orderItemId).serialNumber(serial).startDate(start).endDate(end).warrantyTerms("Điều khoản snapshot").build();
    }
}
