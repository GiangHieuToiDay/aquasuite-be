package com.aquasuite.ims.module.servicedesk;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.CatalogWarrantyApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogWarrantyPolicyDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.api.SalesWarrantyApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderItemWarrantySnapshotDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderWarrantySummaryDto;
import com.aquasuite.ims.module.servicedesk.api.event.WarrantyRecordAuditEvent;
import com.aquasuite.ims.module.servicedesk.internal.entity.WarrantyRecord;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyDisplayStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyStatus;
import com.aquasuite.ims.module.servicedesk.internal.error.ServicedeskErrorCode;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CreateAdHocWarrantyRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CreateDeliveryWarrantyCommand;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CreateWarrantyFromOrderRequest;
import com.aquasuite.ims.module.servicedesk.internal.port.FulfillmentWarrantyPort;
import com.aquasuite.ims.module.servicedesk.internal.repository.TechnicalJobRepository;
import com.aquasuite.ims.module.servicedesk.internal.repository.WarrantyRecordRepository;
import com.aquasuite.ims.module.servicedesk.internal.repository.WarrantyTicketRepository;
import com.aquasuite.ims.module.servicedesk.internal.service.helper.WarrantyCodeGenerator;
import com.aquasuite.ims.module.servicedesk.internal.service.impl.WarrantyRecordServiceImpl;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarrantyRecordServiceTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), VN_ZONE);

    @Mock private WarrantyRecordRepository warrantyRecordRepository;
    @Mock private TechnicalJobRepository technicalJobRepository;
    @Mock private WarrantyTicketRepository warrantyTicketRepository;
    @Mock private WarrantyCodeGenerator warrantyCodeGenerator;
    @Mock private CatalogApi catalogApi;
    @Mock private CatalogWarrantyApi catalogWarrantyApi;
    @Mock private SalesWarrantyApi salesWarrantyApi;
    @Mock private CrmApi crmApi;
    @Mock private IamApi iamApi;
    @Mock private FulfillmentWarrantyPort fulfillmentWarrantyPort;
    @Mock private ApplicationEventPublisher eventPublisher;

    private WarrantyRecordServiceImpl service;

    private final UUID actorId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID orderItemId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();
    private final UUID policyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WarrantyRecordServiceImpl(
                warrantyRecordRepository,
                technicalJobRepository,
                warrantyTicketRepository,
                warrantyCodeGenerator,
                catalogApi,
                catalogWarrantyApi,
                salesWarrantyApi,
                crmApi,
                iamApi,
                fulfillmentWarrantyPort,
                eventPublisher,
                FIXED_CLOCK
        );
        lenient().when(iamApi.getCurrentUserId()).thenReturn(actorId);
        lenient().when(warrantyRecordRepository.saveAndFlush(any(WarrantyRecord.class))).thenAnswer(inv -> {
            WarrantyRecord rec = inv.getArgument(0);
            if (rec.getWarrantyRecordId() == null) {
                rec.setWarrantyRecordId(UUID.randomUUID());
            }
            return rec;
        });
        lenient().when(warrantyRecordRepository.save(any(WarrantyRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CatalogWarrantyPolicyDto activePolicy(int months, boolean serialTracking) {
        return CatalogWarrantyPolicyDto.builder()
                .variantId(variantId)
                .sku("TOTO-CS886")
                .variantName("Trắng sứ")
                .productName("Bồn cầu thông minh TOTO")
                .serialTracking(serialTracking)
                .warrantyPolicyId(policyId)
                .policyCode("WP-24M")
                .policyName("Bảo hành 24 tháng")
                .warrantyMonths(months)
                .warrantyTerms("Bảo hành chính hãng 24 tháng.")
                .policyStatus("ACTIVE")
                .build();
    }

    private void stubDetailRead(WarrantyRecord rec) {
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(
                CatalogSkuSummaryDto.builder().variantId(variantId).sku("TOTO-CS886").productName("TOTO").variantName("White").serialTracking(true).build()
        ));
        when(catalogWarrantyApi.getWarrantyPolicyById(policyId)).thenReturn(Optional.of(activePolicy(24, true)));
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder().customerId(customerId).customerCode("CUS-001").customerName("Nguyễn Văn A").phone("0901234567").build()
        ));
        when(crmApi.getDeliveryAddressesByCustomerId(customerId)).thenReturn(List.of());
        if (rec.getOrderId() != null) {
            when(salesWarrantyApi.getOrderSummaryWithInvoice(orderId)).thenReturn(Optional.of(
                    SalesOrderWarrantySummaryDto.builder().orderId(orderId).orderCode("ORD-001").orderStatus("COMPLETED").paymentStatus("PAID").invoiceCode("INV-001").build()
            ));
        }
        when(technicalJobRepository.findByWarrantyRecordIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("createFromOrder: tính endDate đúng theo policy và phát sự kiện audit")
    void createFromOrderSuccess() {
        var snapshot = SalesOrderItemWarrantySnapshotDto.builder()
                .orderItemId(orderItemId)
                .orderId(orderId)
                .orderCode("ORD-001")
                .orderStatus("COMPLETED")
                .customerId(customerId)
                .variantId(variantId)
                .quantity(BigDecimal.ONE)
                .build();
        when(salesWarrantyApi.lockOrderItemForWarranty(orderItemId)).thenReturn(Optional.of(snapshot));
        when(catalogWarrantyApi.getWarrantyInfoByVariantId(variantId)).thenReturn(Optional.of(activePolicy(24, true)));
        when(warrantyRecordRepository.existsActiveByVariantIdAndSerialNumber(variantId, "SN-100", WarrantyStatus.ACTIVE))
                .thenReturn(false);
        when(warrantyRecordRepository.countByOrderItemIdAndStatus(orderItemId, WarrantyStatus.ACTIVE)).thenReturn(0L);
        when(warrantyCodeGenerator.nextCode(LocalDate.of(2026, 11, 12))).thenReturn("WAR-202611-0001");
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(
                CatalogSkuSummaryDto.builder().variantId(variantId).sku("TOTO-CS886").serialTracking(true).build()
        ));
        when(catalogWarrantyApi.getWarrantyPolicyById(policyId)).thenReturn(Optional.of(activePolicy(24, true)));
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder().customerId(customerId).customerName("Nguyễn Văn A").phone("0901234567").build()
        ));
        when(crmApi.getDeliveryAddressesByCustomerId(customerId)).thenReturn(List.of());
        when(salesWarrantyApi.getOrderSummaryWithInvoice(orderId)).thenReturn(Optional.of(
                SalesOrderWarrantySummaryDto.builder().orderId(orderId).orderCode("ORD-001").orderStatus("COMPLETED").build()
        ));
        when(technicalJobRepository.findByWarrantyRecordIdOrderByCreatedAtDesc(any())).thenReturn(List.of());

        var res = service.createFromOrder(new CreateWarrantyFromOrderRequest(
                orderItemId, "  SN-100  ", LocalDate.of(2026, 11, 12)));

        assertThat(res.warrantyCode()).isEqualTo("WAR-202611-0001");
        assertThat(res.serialNumber()).isEqualTo("SN-100");
        assertThat(res.endDate()).isEqualTo(LocalDate.of(2028, 11, 12));
        assertThat(res.displayStatus()).isEqualTo(WarrantyDisplayStatus.ACTIVE);
        verify(eventPublisher).publishEvent(any(WarrantyRecordAuditEvent.class));
    }

    @Test
    @DisplayName("createFromOrder EF1: thiếu serial khi SKU bật serialTracking -> lỗi WARRANTY_SERIAL_REQUIRED")
    void createFromOrderSerialRequired() {
        var snapshot = SalesOrderItemWarrantySnapshotDto.builder()
                .orderItemId(orderItemId).orderId(orderId).orderStatus("PROCESSING")
                .customerId(customerId).variantId(variantId).quantity(BigDecimal.ONE).build();
        when(salesWarrantyApi.lockOrderItemForWarranty(orderItemId)).thenReturn(Optional.of(snapshot));
        when(catalogWarrantyApi.getWarrantyInfoByVariantId(variantId)).thenReturn(Optional.of(activePolicy(24, true)));

        assertThatThrownBy(() -> service.createFromOrder(new CreateWarrantyFromOrderRequest(
                orderItemId, "   ", LocalDate.of(2026, 11, 12))))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_SERIAL_REQUIRED);
    }

    @Test
    @DisplayName("createFromOrder EF3: SKU không có chính sách hoặc warrantyMonths = 0 -> lỗi WARRANTY_POLICY_NOT_APPLICABLE")
    void createFromOrderPolicyNotApplicable() {
        var snapshot = SalesOrderItemWarrantySnapshotDto.builder()
                .orderItemId(orderItemId).orderId(orderId).orderStatus("COMPLETED")
                .customerId(customerId).variantId(variantId).quantity(BigDecimal.ONE).build();
        when(salesWarrantyApi.lockOrderItemForWarranty(orderItemId)).thenReturn(Optional.of(snapshot));
        when(catalogWarrantyApi.getWarrantyInfoByVariantId(variantId)).thenReturn(Optional.of(activePolicy(0, false)));

        assertThatThrownBy(() -> service.createFromOrder(new CreateWarrantyFromOrderRequest(
                orderItemId, null, LocalDate.of(2026, 11, 12))))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_POLICY_NOT_APPLICABLE);
    }

    @Test
    @DisplayName("createAdHoc: vi phạm unique index khi concurrent -> ánh xạ sang WARRANTY_DUPLICATE_ACTIVE_SERIAL")
    void createAdHocConcurrentDuplicateThrowsConflict() {
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder().customerId(customerId).customerName("Khách A").build()
        ));
        when(catalogWarrantyApi.getWarrantyInfoByVariantId(variantId)).thenReturn(Optional.of(activePolicy(12, true)));
        when(warrantyRecordRepository.existsActiveByVariantIdAndSerialNumber(variantId, "SN-DUP", WarrantyStatus.ACTIVE))
                .thenReturn(false);
        when(warrantyCodeGenerator.nextCode(any())).thenReturn("WAR-202611-0009");
        when(warrantyRecordRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.createAdHoc(new CreateAdHocWarrantyRequest(
                customerId, variantId, null, "SN-DUP", LocalDate.of(2026, 11, 12))))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_DUPLICATE_ACTIVE_SERIAL);
    }

    @Test
    @DisplayName("createFromDelivery: gọi lặp với cùng deliveryId + serialNumber -> trả bản ghi cũ (idempotent)")
    void createFromDeliveryIdempotent() {
        UUID deliveryId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        WarrantyRecord existing = WarrantyRecord.builder()
                .warrantyRecordId(recordId)
                .warrantyCode("WAR-202611-0005")
                .warrantyPolicyId(policyId)
                .customerId(customerId)
                .orderId(orderId)
                .orderItemId(orderItemId)
                .deliveryId(deliveryId)
                .variantId(variantId)
                .serialNumber("SN-DEL-01")
                .startDate(LocalDate.of(2026, 11, 12))
                .endDate(LocalDate.of(2028, 11, 12))
                .warrantyMonths(24)
                .status(WarrantyStatus.ACTIVE)
                .build();

        var snapshot = SalesOrderItemWarrantySnapshotDto.builder()
                .orderItemId(orderItemId).orderId(orderId).orderStatus("PROCESSING")
                .customerId(customerId).variantId(variantId).quantity(BigDecimal.ONE).build();
        when(salesWarrantyApi.lockOrderItemForWarranty(orderItemId)).thenReturn(Optional.of(snapshot));
        when(warrantyRecordRepository.findFirstByVariantIdAndSerialNumberIgnoreCaseAndStatus(
                variantId, "SN-DEL-01", WarrantyStatus.ACTIVE)).thenReturn(Optional.of(existing));
        stubDetailRead(existing);

        var res = service.createFromDelivery(new CreateDeliveryWarrantyCommand(
                deliveryId, orderItemId, "SN-DEL-01", LocalDate.of(2026, 11, 12), actorId));

        assertThat(res.warrantyRecordId()).isEqualTo(recordId);
        verify(warrantyRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("voidRecord: chặn hủy khi đang có WARRANTY_REPAIR job mở")
    void voidRecordBlockedWhenOpenRepairJobExists() {
        UUID recordId = UUID.randomUUID();
        WarrantyRecord active = WarrantyRecord.builder()
                .warrantyRecordId(recordId)
                .warrantyCode("WAR-202611-0002")
                .customerId(customerId)
                .variantId(variantId)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2028, 1, 1))
                .warrantyMonths(24)
                .status(WarrantyStatus.ACTIVE)
                .build();
        when(warrantyRecordRepository.findById(recordId)).thenReturn(Optional.of(active));
        when(technicalJobRepository.existsByWarrantyRecordIdAndJobTypeAndStatusIn(
                recordId, TechnicalJobType.WARRANTY_REPAIR, List.of(TechnicalJobStatus.ASSIGNED, TechnicalJobStatus.IN_PROGRESS)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.voidRecord(recordId, "Nhập nhầm serial"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_HAS_OPEN_JOB);
    }

    @Test
    @DisplayName("voidRecord: thành công cập nhật VOIDED, lưu lý do và phát sự kiện audit")
    void voidRecordSuccess() {
        UUID recordId = UUID.randomUUID();
        WarrantyRecord active = WarrantyRecord.builder()
                .warrantyRecordId(recordId)
                .warrantyCode("WAR-202611-0002")
                .warrantyPolicyId(policyId)
                .customerId(customerId)
                .variantId(variantId)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2028, 1, 1))
                .warrantyMonths(24)
                .status(WarrantyStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();
        when(warrantyRecordRepository.findById(recordId)).thenReturn(Optional.of(active));
        when(technicalJobRepository.existsByWarrantyRecordIdAndJobTypeAndStatusIn(any(), any(), any())).thenReturn(false);
        stubDetailRead(active);

        var res = service.voidRecord(recordId, "Khách đổi trả hàng");

        assertThat(res.status()).isEqualTo(WarrantyStatus.VOIDED);
        assertThat(res.displayStatus()).isEqualTo(WarrantyDisplayStatus.VOIDED);
        assertThat(res.voidReason()).isEqualTo("Khách đổi trả hàng");
        verify(eventPublisher).publishEvent(any(WarrantyRecordAuditEvent.class));
    }

    @Test
    @DisplayName("getMyRecordDetail: khách hàng khác xem thẻ không thuộc mình -> WARRANTY_FORBIDDEN")
    void getMyRecordDetailForbiddenForOtherCustomer() {
        UUID recordId = UUID.randomUUID();
        UUID anotherCustomerId = UUID.randomUUID();
        when(crmApi.getCustomerByUserId(actorId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder().customerId(customerId).build()
        ));
        when(warrantyRecordRepository.findById(recordId)).thenReturn(Optional.of(
                WarrantyRecord.builder().warrantyRecordId(recordId).customerId(anotherCustomerId).build()
        ));

        assertThatThrownBy(() -> service.getMyRecordDetail(recordId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_FORBIDDEN);
    }
}

