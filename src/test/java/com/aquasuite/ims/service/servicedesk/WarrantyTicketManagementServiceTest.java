package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.entity.servicedesk.WarrantyRecord;
import com.aquasuite.ims.entity.servicedesk.WarrantyTicket;
import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import com.aquasuite.ims.dto.servicedesk.request.DispatchWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.RejectWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.response.TechnicalJobDetailResponse;
import com.aquasuite.ims.repository.servicedesk.WarrantyRecordRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyTicketRepository;
import com.aquasuite.ims.service.servicedesk.TechnicalJobService;
import com.aquasuite.ims.service.servicedesk.impl.WarrantyTicketManagementServiceImpl;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho quản lý ticket phía nội bộ: bắn job (dispatch), từ chối tại bàn (reject), cờ hết hạn.
 */
@ExtendWith(MockitoExtension.class)
class WarrantyTicketManagementServiceTest {

    @Mock private WarrantyTicketRepository warrantyTicketRepository;
    @Mock private WarrantyRecordRepository warrantyRecordRepository;
    @Mock private CatalogLookupService catalogApi;
    @Mock private TechnicalJobService technicalJobService;
    @Mock private IamQueryService iamApi;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(LocalDate.of(2026, 11, 18).atStartOfDay(ZoneOffset.UTC).toInstant(),
            ZoneId.of("UTC"));
    private WarrantyTicketManagementServiceImpl service;

    private final UUID technicianId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new WarrantyTicketManagementServiceImpl(
                warrantyTicketRepository, warrantyRecordRepository, catalogApi, technicalJobService,
                iamApi, eventPublisher, clock);
        lenient().when(warrantyTicketRepository.save(any(WarrantyTicket.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(warrantyRecordRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(catalogApi.getSkuSummary(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(iamApi.getCurrentUserId()).thenReturn(UUID.randomUUID());
    }

    private WarrantyTicket ticket(WarrantyTicketStatus status) {
        return WarrantyTicket.builder()
                .ticketId(UUID.randomUUID())
                .ticketCode("TCK-WAR-202611-0001")
                .warrantyRecordId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .issueDescription("Máy rò nước")
                .siteAddress("123 Trần Phú")
                .contactName("A").contactPhone("0900")
                .status(status)
                .build();
    }

    @Test
    @DisplayName("dispatch: ticket NEW -> tạo job WARRANTY_REPAIR, ticket chuyển IN_PROGRESS + gắn jobId")
    void dispatchFromNew() {
        WarrantyTicket t = ticket(WarrantyTicketStatus.NEW);
        UUID jobId = UUID.randomUUID();
        when(warrantyTicketRepository.findById(t.getTicketId())).thenReturn(Optional.of(t));
        when(technicalJobService.dispatchWarrantyRepairJob(
                eq(t.getCustomerId()), eq(t.getWarrantyRecordId()), eq(t.getTicketId()),
                eq(technicianId), any(), eq(t.getIssueDescription()), any()))
                .thenReturn(TechnicalJobDetailResponse.builder().technicalJobId(jobId).build());

        var res = service.dispatch(t.getTicketId(), new DispatchWarrantyTicketRequest(technicianId, null, null));

        assertThat(res.status()).isEqualTo(WarrantyTicketStatus.IN_PROGRESS);
        assertThat(res.technicalJobId()).isEqualTo(jobId);
    }

    @Test
    @DisplayName("dispatch: địa chỉ để trống -> dùng địa chỉ khách ghi trên ticket")
    void dispatchUsesTicketAddressWhenBlank() {
        WarrantyTicket t = ticket(WarrantyTicketStatus.NEW);
        when(warrantyTicketRepository.findById(t.getTicketId())).thenReturn(Optional.of(t));
        when(technicalJobService.dispatchWarrantyRepairJob(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(TechnicalJobDetailResponse.builder().technicalJobId(UUID.randomUUID()).build());

        service.dispatch(t.getTicketId(), new DispatchWarrantyTicketRequest(technicianId, null, "  "));

        ArgumentCaptor<String> address = ArgumentCaptor.forClass(String.class);
        verify(technicalJobService).dispatchWarrantyRepairJob(any(), any(), any(), any(),
                address.capture(), any(), any());
        assertThat(address.getValue()).isEqualTo(t.getSiteAddress());
    }

    @Test
    @DisplayName("dispatch: ticket không ở NEW -> lỗi, không tạo job")
    void dispatchRejectsNonNew() {
        WarrantyTicket t = ticket(WarrantyTicketStatus.IN_PROGRESS);
        when(warrantyTicketRepository.findById(t.getTicketId())).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.dispatch(t.getTicketId(),
                new DispatchWarrantyTicketRequest(technicianId, null, null)))
                .isInstanceOf(AppException.class);

        verify(technicalJobService, never()).dispatchWarrantyRepairJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("dispatch: ticket không tồn tại -> lỗi NOT_FOUND")
    void dispatchTicketNotFound() {
        UUID id = UUID.randomUUID();
        when(warrantyTicketRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dispatch(id, new DispatchWarrantyTicketRequest(technicianId, null, null)))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("reject: ticket NEW -> REJECTED, lưu lý do, phát audit event")
    void rejectFromNew() {
        WarrantyTicket t = ticket(WarrantyTicketStatus.NEW);
        when(warrantyTicketRepository.findById(t.getTicketId())).thenReturn(Optional.of(t));

        var res = service.reject(t.getTicketId(), new RejectWarrantyTicketRequest("Thẻ đã hết hạn bảo hành"));

        assertThat(res.status()).isEqualTo(WarrantyTicketStatus.REJECTED);
        assertThat(res.rejectionReason()).isEqualTo("Thẻ đã hết hạn bảo hành");
        verify(eventPublisher).publishEvent(any(com.aquasuite.ims.event.servicedesk.WarrantyTicketAuditEvent.class));
    }

    @Test
    @DisplayName("reject: ticket không ở NEW -> lỗi NOT_REJECTABLE")
    void rejectRejectsNonNew() {
        WarrantyTicket t = ticket(WarrantyTicketStatus.IN_PROGRESS);
        when(warrantyTicketRepository.findById(t.getTicketId())).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.reject(t.getTicketId(), new RejectWarrantyTicketRequest("x")))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("reject: thiếu lý do -> lỗi REJECT_REASON_REQUIRED")
    void rejectRequiresReason() {
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), new RejectWarrantyTicketRequest("   ")))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("getById: thẻ hết hạn -> cờ warrantyExpired = true")
    void detailFlagsExpiredWarranty() {
        WarrantyTicket t = ticket(WarrantyTicketStatus.NEW);
        WarrantyRecord expiredRecord = WarrantyRecord.builder()
                .warrantyRecordId(t.getWarrantyRecordId())
                .warrantyCode("WAR-1")
                .customerId(t.getCustomerId())
                .variantId(UUID.randomUUID())
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2026, 1, 1)) // trước mốc clock 2026-11-18
                .warrantyMonths(24)
                .build();
        when(warrantyTicketRepository.findById(t.getTicketId())).thenReturn(Optional.of(t));
        when(warrantyRecordRepository.findById(t.getWarrantyRecordId())).thenReturn(Optional.of(expiredRecord));

        var res = service.getById(t.getTicketId());

        assertThat(res.warrantyExpired()).isTrue();
        assertThat(res.warrantyEndDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("getById: thẻ còn hạn -> cờ warrantyExpired = false")
    void detailNotExpiredWhenValid() {
        WarrantyTicket t = ticket(WarrantyTicketStatus.NEW);
        WarrantyRecord validRecord = WarrantyRecord.builder()
                .warrantyRecordId(t.getWarrantyRecordId())
                .warrantyCode("WAR-2")
                .customerId(t.getCustomerId())
                .variantId(UUID.randomUUID())
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2028, 1, 1))
                .warrantyMonths(24)
                .build();
        when(warrantyTicketRepository.findById(t.getTicketId())).thenReturn(Optional.of(t));
        when(warrantyRecordRepository.findById(t.getWarrantyRecordId())).thenReturn(Optional.of(validRecord));

        var res = service.getById(t.getTicketId());

        assertThat(res.warrantyExpired()).isFalse();
    }
}
