package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.entity.servicedesk.WarrantyRecord;
import com.aquasuite.ims.entity.servicedesk.WarrantyTicket;
import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import com.aquasuite.ims.dto.servicedesk.request.CompleteWarrantyTicketRequest;
import com.aquasuite.ims.exception.servicedesk.ServicedeskErrorCode;
import com.aquasuite.ims.dto.servicedesk.request.RejectWarrantyTicketRequest;
import com.aquasuite.ims.repository.servicedesk.WarrantyRecordRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyTicketRepository;
import com.aquasuite.ims.service.servicedesk.impl.WarrantyTicketServiceImpl;
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
import java.util.List;
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
 * Unit test cho quản lý ticket phía nội bộ: tiếp nhận xử lý, hoàn tất, từ chối tại bàn (reject), cờ hết hạn.
 */
@ExtendWith(MockitoExtension.class)
class WarrantyTicketManagementServiceTest {

    @Mock private WarrantyTicketRepository warrantyTicketRepository;
    @Mock private WarrantyRecordRepository warrantyRecordRepository;
    @Mock private CatalogLookupService catalogApi;
    @Mock private IamQueryService iamApi;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(LocalDate.of(2026, 11, 18).atStartOfDay(ZoneOffset.UTC).toInstant(),
            ZoneId.of("UTC"));
    private WarrantyTicketServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WarrantyTicketServiceImpl(
                warrantyTicketRepository, warrantyRecordRepository, catalogApi,
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
    @DisplayName("startProcessing: ticket NEW -> IN_PROGRESS")
    void startFromNew() {
        WarrantyTicket t = ticket(WarrantyTicketStatus.NEW);
        when(warrantyTicketRepository.findById(t.getTicketId())).thenReturn(Optional.of(t));

        var res = service.startProcessing(t.getTicketId());

        assertThat(res.status()).isEqualTo(WarrantyTicketStatus.IN_PROGRESS);
        verify(warrantyTicketRepository).save(t);
    }

    @Test
    @DisplayName("startProcessing: ticket không ở NEW -> SVD_039, không lưu")
    void startRejectsNonNew() {
        for (WarrantyTicketStatus st : List.of(WarrantyTicketStatus.IN_PROGRESS, WarrantyTicketStatus.DONE,
                WarrantyTicketStatus.REJECTED, WarrantyTicketStatus.CANCELLED)) {
            WarrantyTicket t = ticket(st);
            when(warrantyTicketRepository.findById(t.getTicketId())).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.startProcessing(t.getTicketId()))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                            .isEqualTo(ServicedeskErrorCode.WARRANTY_TICKET_NOT_STARTABLE));
        }
        verify(warrantyTicketRepository, never()).save(any());
    }

    @Test
    @DisplayName("startProcessing: ticket không tồn tại -> SVD_023")
    void startTicketNotFound() {
        UUID id = UUID.randomUUID();
        when(warrantyTicketRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startProcessing(id))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ServicedeskErrorCode.WARRANTY_TICKET_NOT_FOUND));
    }

    @Test
    @DisplayName("complete: ticket IN_PROGRESS -> DONE, lưu kết quả và thời điểm hoàn tất")
    void completeFromInProgress() {
        WarrantyTicket t = ticket(WarrantyTicketStatus.IN_PROGRESS);
        when(warrantyTicketRepository.findById(t.getTicketId())).thenReturn(Optional.of(t));

        var res = service.complete(t.getTicketId(), new CompleteWarrantyTicketRequest("  Không bảo hành vì lỗi do va đập  "));

        assertThat(res.status()).isEqualTo(WarrantyTicketStatus.DONE);
        assertThat(t.getResult()).isEqualTo("Không bảo hành vì lỗi do va đập");
        assertThat(t.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("complete: ticket không ở IN_PROGRESS -> SVD_040; thiếu kết quả -> SVD_041")
    void completeRejectedCases() {
        WarrantyTicket t = ticket(WarrantyTicketStatus.NEW);
        when(warrantyTicketRepository.findById(t.getTicketId())).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.complete(t.getTicketId(), new CompleteWarrantyTicketRequest("Đã sửa")))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ServicedeskErrorCode.WARRANTY_TICKET_NOT_COMPLETABLE));

        assertThatThrownBy(() -> service.complete(UUID.randomUUID(), new CompleteWarrantyTicketRequest("   ")))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ServicedeskErrorCode.WARRANTY_TICKET_RESULT_REQUIRED));
        verify(warrantyTicketRepository, never()).save(any());
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

    @Test
    @DisplayName("getById: không tìm thấy ticket -> ném WARRANTY_TICKET_NOT_FOUND")
    void getByIdThrowsWhenNotFound() {
        UUID missingId = UUID.randomUUID();
        when(warrantyTicketRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(missingId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ServicedeskErrorCode.WARRANTY_TICKET_NOT_FOUND));
    }

    @Test
    @DisplayName("list: phân trang, lọc trạng thái và bổ sung thông tin thẻ bảo hành / SKU")
    @SuppressWarnings("unchecked")
    void listEnrichesRecordAndSku() {
        WarrantyTicket t = ticket(WarrantyTicketStatus.NEW);
        UUID variantId = UUID.randomUUID();
        WarrantyRecord record = WarrantyRecord.builder()
                .warrantyRecordId(t.getWarrantyRecordId())
                .warrantyCode("WAR-202611-0077")
                .variantId(variantId)
                .serialNumber("SN-777")
                .build();

        org.springframework.data.domain.Page<WarrantyTicket> page = new org.springframework.data.domain.PageImpl<>(
                List.of(t),
                org.springframework.data.domain.PageRequest.of(0, 20),
                1
        );
        when(warrantyTicketRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);
        when(warrantyRecordRepository.findAllById(any())).thenReturn(List.of(record));
        when(catalogApi.getSkuSummaries(java.util.Set.of(variantId))).thenReturn(java.util.Map.of(
                variantId,
                com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto.builder()
                        .variantId(variantId)
                        .productName("Bồn cầu TOTO")
                        .variantName("Trắng sứ")
                        .build()
        ));

        var filter = new com.aquasuite.ims.dto.servicedesk.request.WarrantyTicketFilterRequest();
        filter.setStatus(WarrantyTicketStatus.NEW);
        var res = service.list(filter);

        assertThat(res.getItems()).hasSize(1);
        var item = res.getItems().get(0);
        assertThat(item.ticketCode()).isEqualTo("TCK-WAR-202611-0001");
        assertThat(item.warrantyCode()).isEqualTo("WAR-202611-0077");
        assertThat(item.serialNumber()).isEqualTo("SN-777");
        assertThat(item.productName()).isEqualTo("Bồn cầu TOTO");
        assertThat(item.variantName()).isEqualTo("Trắng sứ");
        assertThat(item.canCancel()).isFalse();
    }
}
