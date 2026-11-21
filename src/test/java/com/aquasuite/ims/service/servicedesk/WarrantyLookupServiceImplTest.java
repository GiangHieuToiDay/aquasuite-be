package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.sales.SalesOrderService;
import com.aquasuite.ims.dto.sales.response.SalesOrderItemSummaryDto;
import com.aquasuite.ims.entity.servicedesk.WarrantyRecord;
import com.aquasuite.ims.enums.servicedesk.WarrantyStatus;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyLookupResponse;
import com.aquasuite.ims.repository.servicedesk.WarrantyRecordRepository;
import com.aquasuite.ims.service.servicedesk.impl.WarrantyLookupServiceImpl;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WarrantyLookupServiceImplTest {
    private final WarrantyRecordRepository warrantyRecords = mock(WarrantyRecordRepository.class);
    private final IamQueryService iamApi = mock(IamQueryService.class);
    private final CustomerService crmApi = mock(CustomerService.class);
    private final CatalogLookupService catalogApi = mock(CatalogLookupService.class);
    private final SalesOrderService salesOrderApi = mock(SalesOrderService.class);
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
        WarrantyRecord record = record(customerId, variantId, orderItemId, "SN-CS886-260101",
                LocalDate.of(2026, 11, 13), LocalDate.of(2036, 11, 13), WarrantyStatus.ACTIVE);
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
        WarrantyRecord record = record(customerId, variantId, orderItemId, "SN-OLD",
                LocalDate.of(2023, 1, 1), LocalDate.of(2026, 11, 14), WarrantyStatus.ACTIVE);
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

    @Test
    void marksVoidedRecordEvenWhenEndDateIsInFutureAndNeverAllowsClaim() {
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        WarrantyRecord voidedRecord = record(customerId, variantId, orderItemId, "SN-VOID-2036",
                LocalDate.of(2026, 11, 1), LocalDate.of(2036, 11, 1), WarrantyStatus.VOIDED);
        when(iamApi.getCurrentUserId()).thenReturn(userId);
        when(crmApi.getCustomerByUserId(userId)).thenReturn(Optional.of(CustomerSummaryDto.builder().customerId(customerId).build()));
        when(warrantyRecords.findByCustomerIdOrderByStartDateDesc(customerId)).thenReturn(List.of(voidedRecord));
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(CatalogSkuSummaryDto.builder()
                .productName("Sen tắm TOTO").variantName("TBG04302V").sku("TBG04302V").build()));
        when(salesOrderApi.getOrderItemSummary(orderItemId)).thenReturn(Optional.of(SalesOrderItemSummaryDto.builder()
                .orderCode("ORD-202611-009").build()));

        WarrantyLookupResponse response = service.findMine(null).getFirst();

        assertThat(response.status()).isEqualTo("VOIDED");
        assertThat(response.remainingDays()).isZero();
        assertThat(response.claimAllowed()).isFalse();
        assertThat(response.orderCode()).isEqualTo("ORD-202611-009");
    }

    @Test
    void filtersByOrderCodeCaseInsensitivelyAndKeepsLastDayActive() {
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        WarrantyRecord lastDayRecord = record(customerId, variantId, orderItemId, null,
                LocalDate.of(2025, 11, 15), LocalDate.of(2026, 11, 15), WarrantyStatus.ACTIVE);
        when(iamApi.getCurrentUserId()).thenReturn(userId);
        when(crmApi.getCustomerByUserId(userId)).thenReturn(Optional.of(CustomerSummaryDto.builder().customerId(customerId).build()));
        when(warrantyRecords.findByCustomerIdOrderByStartDateDesc(customerId)).thenReturn(List.of(lastDayRecord));
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.empty());
        when(salesOrderApi.getOrderItemSummary(orderItemId)).thenReturn(Optional.of(SalesOrderItemSummaryDto.builder()
                .orderCode("ORD-202511-777").productName("Vòi chậu INAX").variantName("LFV-1402S").sku("LFV-1402S").build()));

        List<WarrantyLookupResponse> matched = service.findMine("  ord-202511-777 ");
        List<WarrantyLookupResponse> unmatched = service.findMine("ORD-OTHER");

        assertThat(matched).singleElement().satisfies(response -> {
            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.remainingDays()).isZero();
            assertThat(response.claimAllowed()).isTrue();
            assertThat(response.productName()).isEqualTo("Vòi chậu INAX");
        });
        assertThat(unmatched).isEmpty();
    }

    @Test
    void returnsEmptyListWhenUserHasNoLinkedCrmProfile() {
        UUID userId = UUID.randomUUID();
        when(iamApi.getCurrentUserId()).thenReturn(userId);
        when(crmApi.getCustomerByUserId(userId)).thenReturn(Optional.empty());

        assertThat(service.findMine("SN-ANY")).isEmpty();
        verifyNoInteractions(warrantyRecords);
    }

    private WarrantyRecord record(UUID customerId, UUID variantId, UUID orderItemId, String serial,
                                  LocalDate start, LocalDate end, WarrantyStatus status) {
        return WarrantyRecord.builder().warrantyRecordId(UUID.randomUUID()).customerId(customerId).variantId(variantId)
                .orderItemId(orderItemId).serialNumber(serial).startDate(start).endDate(end)
                .status(status).warrantyTerms("Điều khoản snapshot").build();
    }
}
