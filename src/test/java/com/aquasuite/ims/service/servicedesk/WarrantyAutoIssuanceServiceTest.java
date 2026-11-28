package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto;
import com.aquasuite.ims.dto.servicedesk.request.AutoIssueWarrantyCommand;
import com.aquasuite.ims.service.catalog.CatalogWarrantyLookupService;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyAutoIssuanceCandidateReader;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyAutoIssuanceCandidateReader.CompletedOrderItemCandidate;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyAutoIssuanceCandidateReader.DeliveredItemCandidate;
import com.aquasuite.ims.service.servicedesk.impl.WarrantyAutoIssuanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarrantyAutoIssuanceServiceTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), VN_ZONE);

    @Mock private WarrantyAutoIssuanceCandidateReader candidateReader;
    @Mock private WarrantyRecordService warrantyRecordService;
    @Mock private CatalogWarrantyLookupService catalogWarrantyApi;

    private WarrantyAutoIssuanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WarrantyAutoIssuanceServiceImpl(
                candidateReader,
                warrantyRecordService,
                catalogWarrantyApi,
                FIXED_CLOCK
        );
    }

    @Test
    @DisplayName("syncEligibleRecords: xử lý delivery DELIVERED (non-serial & serial), cache policy, bỏ qua SKU không có BH")
    void syncDeliveredDeliveriesHandlesSerialNonSerialAndNoPolicy() {
        UUID d1 = UUID.randomUUID();
        UUID oi1 = UUID.randomUUID();
        UUID vNonSerial = UUID.randomUUID();

        UUID d2 = UUID.randomUUID();
        UUID oi2 = UUID.randomUUID();
        UUID vSerial = UUID.randomUUID();

        UUID d3 = UUID.randomUUID();
        UUID oi3 = UUID.randomUUID();
        UUID oi4 = UUID.randomUUID();
        UUID vNoWarranty = UUID.randomUUID();

        // Delivered at 2026-11-12T18:30:00Z = 2026-11-13T01:30:00+07:00 in store zone
        OffsetDateTime deliveredUtc = OffsetDateTime.parse("2026-11-12T18:30:00Z");

        when(candidateReader.findDeliveredDeliveryCandidates(anyInt())).thenReturn(List.of(
                new DeliveredItemCandidate(d1, oi1, vNonSerial, deliveredUtc, deliveredUtc, List.of()),
                new DeliveredItemCandidate(d2, oi2, vSerial, deliveredUtc, deliveredUtc,
                        Arrays.asList("SN-01", "  SN-02  ", "SN-01", " ", null)),
                new DeliveredItemCandidate(d3, oi3, vSerial, deliveredUtc, deliveredUtc, List.of()),
                new DeliveredItemCandidate(d3, oi4, vNoWarranty, deliveredUtc, deliveredUtc, List.of())
        ));
        when(candidateReader.findCompletedOrderWithoutDeliveryCandidates(anyInt())).thenReturn(List.of());

        when(catalogWarrantyApi.getWarrantyInfoByVariantId(vNonSerial)).thenReturn(Optional.of(
                CatalogWarrantyPolicyDto.builder()
                        .variantId(vNonSerial).warrantyPolicyId(UUID.randomUUID())
                        .warrantyMonths(12).serialTracking(false).policyStatus("ACTIVE").build()
        ));
        when(catalogWarrantyApi.getWarrantyInfoByVariantId(vSerial)).thenReturn(Optional.of(
                CatalogWarrantyPolicyDto.builder()
                        .variantId(vSerial).warrantyPolicyId(UUID.randomUUID())
                        .warrantyMonths(24).serialTracking(true).policyStatus("ACTIVE").build()
        ));
        when(catalogWarrantyApi.getWarrantyInfoByVariantId(vNoWarranty)).thenReturn(Optional.of(
                CatalogWarrantyPolicyDto.builder()
                        .variantId(vNoWarranty).warrantyPolicyId(null).warrantyMonths(0).build()
        ));

        when(warrantyRecordService.autoIssueIfEligible(any())).thenReturn(true);

        int count = service.syncEligibleRecords();

        assertThat(count).isEqualTo(3);

        ArgumentCaptor<AutoIssueWarrantyCommand> captor = ArgumentCaptor.forClass(AutoIssueWarrantyCommand.class);
        verify(warrantyRecordService, times(3)).autoIssueIfEligible(captor.capture());

        List<AutoIssueWarrantyCommand> cmds = captor.getAllValues();
        assertThat(cmds.get(0)).isEqualTo(new AutoIssueWarrantyCommand(
                d1, oi1, null, LocalDate.of(2026, 11, 13), null));
        assertThat(cmds.get(1)).isEqualTo(new AutoIssueWarrantyCommand(
                d2, oi2, "SN-01", LocalDate.of(2026, 11, 13), null));
        assertThat(cmds.get(2)).isEqualTo(new AutoIssueWarrantyCommand(
                d2, oi2, "SN-02", LocalDate.of(2026, 11, 13), null));

        // vSerial appeared twice in candidates, must hit catalog lookup only once via per-pass cache
        verify(catalogWarrantyApi, times(1)).getWarrantyInfoByVariantId(vSerial);
    }

    @Test
    @DisplayName("syncEligibleRecords: đơn COMPLETED không có phiếu giao cấp thẻ cho non-serial và cô lập lỗi từng dòng")
    void syncCompletedPickupOrdersAndIsolateErrors() {
        UUID oiBad = UUID.randomUUID();
        UUID oiGood = UUID.randomUUID();
        UUID oiSerial = UUID.randomUUID();
        UUID vNonSerial = UUID.randomUUID();
        UUID vSerial = UUID.randomUUID();
        OffsetDateTime orderTime = OffsetDateTime.parse("2026-11-11T09:00:00+07:00");

        when(candidateReader.findDeliveredDeliveryCandidates(anyInt())).thenReturn(List.of());
        when(candidateReader.findCompletedOrderWithoutDeliveryCandidates(anyInt())).thenReturn(List.of(
                new CompletedOrderItemCandidate(oiBad, vNonSerial, orderTime, orderTime),
                new CompletedOrderItemCandidate(oiGood, vNonSerial, orderTime, orderTime),
                new CompletedOrderItemCandidate(oiSerial, vSerial, orderTime, orderTime)
        ));

        when(catalogWarrantyApi.getWarrantyInfoByVariantId(vNonSerial)).thenReturn(Optional.of(
                CatalogWarrantyPolicyDto.builder()
                        .variantId(vNonSerial).warrantyPolicyId(UUID.randomUUID())
                        .warrantyMonths(18).serialTracking(false).policyStatus("ACTIVE").build()
        ));
        when(catalogWarrantyApi.getWarrantyInfoByVariantId(vSerial)).thenReturn(Optional.of(
                CatalogWarrantyPolicyDto.builder()
                        .variantId(vSerial).warrantyPolicyId(UUID.randomUUID())
                        .warrantyMonths(18).serialTracking(true).policyStatus("ACTIVE").build()
        ));

        when(warrantyRecordService.autoIssueIfEligible(new AutoIssueWarrantyCommand(
                null, oiBad, null, LocalDate.of(2026, 11, 11), null)))
                .thenThrow(new RuntimeException("Simulated per-item error"));
        when(warrantyRecordService.autoIssueIfEligible(new AutoIssueWarrantyCommand(
                null, oiGood, null, LocalDate.of(2026, 11, 11), null)))
                .thenReturn(true);

        int count = service.syncEligibleRecords();

        assertThat(count).isEqualTo(1);
        verify(warrantyRecordService, times(2)).autoIssueIfEligible(any());
    }
}
