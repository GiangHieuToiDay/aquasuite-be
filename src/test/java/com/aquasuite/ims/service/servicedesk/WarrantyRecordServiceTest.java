package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.service.catalog.CatalogWarrantyLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.sales.SalesQueryService;
import com.aquasuite.ims.dto.sales.response.SalesOrderItemWarrantySnapshotDto;
import com.aquasuite.ims.dto.sales.response.SalesOrderWarrantySummaryDto;
import com.aquasuite.ims.event.servicedesk.WarrantyRecordAuditEvent;
import com.aquasuite.ims.entity.servicedesk.WarrantyTicket;
import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import com.aquasuite.ims.entity.servicedesk.WarrantyRecord;
import com.aquasuite.ims.enums.servicedesk.WarrantyDisplayStatus;
import com.aquasuite.ims.enums.servicedesk.WarrantyStatus;
import com.aquasuite.ims.exception.servicedesk.ServicedeskErrorCode;
import com.aquasuite.ims.dto.servicedesk.request.CreateAdHocWarrantyRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateDeliveryWarrantyCommand;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyFromOrderRequest;
import com.aquasuite.ims.integration.servicedesk.FulfillmentWarrantyPort;
import com.aquasuite.ims.repository.servicedesk.WarrantyRecordRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyTicketRepository;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyCodeGenerator;
import com.aquasuite.ims.service.servicedesk.impl.WarrantyRecordServiceImpl;
import com.aquasuite.ims.exception.AppException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarrantyRecordServiceTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), VN_ZONE);

    @Mock private WarrantyRecordRepository warrantyRecordRepository;
    @Mock private WarrantyTicketRepository warrantyTicketRepository;
    @Mock private WarrantyCodeGenerator warrantyCodeGenerator;
    @Mock private CatalogLookupService catalogApi;
    @Mock private CatalogWarrantyLookupService catalogWarrantyApi;
    @Mock private SalesQueryService salesWarrantyApi;
    @Mock private CustomerService crmApi;
    @Mock private IamQueryService iamApi;
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
                warrantyTicketRepository,
                warrantyCodeGenerator,
                catalogApi,
                catalogWarrantyApi,
                crmApi,
                iamApi,
                fulfillmentWarrantyPort,
                eventPublisher,
                FIXED_CLOCK,
                salesWarrantyApi
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
        when(warrantyTicketRepository.findFirstByWarrantyRecordIdAndStatusIn(eq(recordId), any()))
                .thenReturn(Optional.of(WarrantyTicket.builder().ticketCode("WT-202611-0001").status(WarrantyTicketStatus.NEW).build()));

        assertThatThrownBy(() -> service.voidRecord(recordId, "Nhập nhầm serial"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_HAS_OPEN_TICKET);
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

    @Test
    @DisplayName("createFromOrder: thiếu orderItem, đơn CANCELLED hoặc vượt số lượng mua -> ném lỗi đúng")
    void createFromOrder_validationGuards_throwExpectedErrors() {
        UUID missingItemId = UUID.randomUUID();
        when(salesWarrantyApi.lockOrderItemForWarranty(missingItemId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createFromOrder(new CreateWarrantyFromOrderRequest(
                missingItemId, "SN-1", LocalDate.of(2026, 11, 12))))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_ORDER_ITEM_NOT_FOUND);

        var cancelledOrderSnapshot = SalesOrderItemWarrantySnapshotDto.builder()
                .orderItemId(orderItemId).orderId(orderId).orderStatus("CANCELLED")
                .customerId(customerId).variantId(variantId).quantity(BigDecimal.ONE).build();
        when(salesWarrantyApi.lockOrderItemForWarranty(orderItemId)).thenReturn(Optional.of(cancelledOrderSnapshot));
        assertThatThrownBy(() -> service.createFromOrder(new CreateWarrantyFromOrderRequest(
                orderItemId, "SN-1", LocalDate.of(2026, 11, 12))))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_ORDER_NOT_ELIGIBLE);

        var activeSnapshot = SalesOrderItemWarrantySnapshotDto.builder()
                .orderItemId(orderItemId).orderId(orderId).orderStatus("COMPLETED")
                .customerId(customerId).variantId(variantId).quantity(BigDecimal.ONE).build();
        when(salesWarrantyApi.lockOrderItemForWarranty(orderItemId)).thenReturn(Optional.of(activeSnapshot));
        when(catalogWarrantyApi.getWarrantyInfoByVariantId(variantId)).thenReturn(Optional.of(activePolicy(24, false)));
        when(warrantyRecordRepository.countByOrderItemIdAndStatus(orderItemId, WarrantyStatus.ACTIVE)).thenReturn(1L);
        assertThatThrownBy(() -> service.createFromOrder(new CreateWarrantyFromOrderRequest(
                orderItemId, null, LocalDate.of(2026, 11, 12))))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_QUANTITY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("createAdHoc: cấp thẻ ad-hoc với override policy thành công, không yêu cầu serial khi serialTracking=false")
    void createAdHoc_WithOverridePolicy_Success() {
        UUID overridePolicyId = UUID.randomUUID();
        CatalogWarrantyPolicyDto overridePolicy = CatalogWarrantyPolicyDto.builder()
                .variantId(variantId).warrantyPolicyId(overridePolicyId)
                .policyCode("WP-36M").policyName("Bảo hành mở rộng 36 tháng")
                .warrantyMonths(36).policyStatus("ACTIVE").serialTracking(false).build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder().customerId(customerId).customerName("Nguyễn Văn A").phone("0901234567").build()
        ));
        when(catalogWarrantyApi.getWarrantyInfoByVariantId(variantId)).thenReturn(Optional.of(activePolicy(12, false)));
        when(catalogWarrantyApi.getWarrantyPolicyById(overridePolicyId)).thenReturn(Optional.of(overridePolicy));
        when(warrantyCodeGenerator.nextCode(LocalDate.of(2026, 11, 12))).thenReturn("WAR-202611-0099");
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(
                CatalogSkuSummaryDto.builder().variantId(variantId).sku("TOTO-CS886").serialTracking(false).build()
        ));
        when(crmApi.getDeliveryAddressesByCustomerId(customerId)).thenReturn(List.of());

        var res = service.createAdHoc(new CreateAdHocWarrantyRequest(
                customerId, variantId, overridePolicyId, null, LocalDate.of(2026, 11, 12)));

        assertThat(res.warrantyCode()).isEqualTo("WAR-202611-0099");
        assertThat(res.endDate()).isEqualTo(LocalDate.of(2029, 11, 12));
        assertThat(res.displayStatus()).isEqualTo(WarrantyDisplayStatus.ACTIVE);
        verify(eventPublisher).publishEvent(any(WarrantyRecordAuditEvent.class));
    }

    @Test
    @DisplayName("searchRecords: số điện thoại không khớp khách hàng -> trả trang rỗng không query repository")
    void searchRecords_UnknownPhone_ReturnsEmptyWithoutQuery() {
        when(crmApi.findCustomerIdsByPhoneKeyword("0999000111")).thenReturn(List.of());
        var filter = new com.aquasuite.ims.dto.servicedesk.request.WarrantyRecordFilterRequest();
        filter.setPhone("0999000111");

        var res = service.searchRecords(filter);

        assertThat(res.getItems()).isEmpty();
        assertThat(res.getTotalElements()).isZero();
        verify(warrantyRecordRepository, never()).findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    @DisplayName("searchRecords: phân trang, map CLAIMED displayStatus và trả openTicketCode")
    @SuppressWarnings("unchecked")
    void searchRecords_EnrichesOpenTicketAndClaimedStatus() {
        UUID recId = UUID.randomUUID();
        WarrantyRecord record = WarrantyRecord.builder()
                .warrantyRecordId(recId)
                .warrantyCode("WAR-202611-0010")
                .customerId(customerId)
                .orderId(orderId)
                .variantId(variantId)
                .serialNumber("SN-CLAIM-1")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2028, 1, 1))
                .warrantyMonths(24)
                .status(WarrantyStatus.ACTIVE)
                .build();

        org.springframework.data.domain.Page<WarrantyRecord> page = new org.springframework.data.domain.PageImpl<>(
                List.of(record), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(warrantyRecordRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        when(catalogApi.getSkuSummaries(any())).thenReturn(java.util.Map.of(
                variantId, CatalogSkuSummaryDto.builder().variantId(variantId).sku("TOTO-1").productName("Sen TOTO").variantName("Bạc").build()
        ));
        when(crmApi.getCustomersByIds(any())).thenReturn(java.util.Map.of(
                customerId, CustomerSummaryDto.builder().customerId(customerId).customerName("Trần Thị B").phone("0912345678").build()
        ));
        when(salesWarrantyApi.getOrderCodesByIds(any())).thenReturn(java.util.Map.of(orderId, "ORD-010"));
        when(warrantyTicketRepository.findTicketsByWarrantyRecordIdsAndStatuses(any(), any())).thenReturn(List.of(
                WarrantyTicket.builder().warrantyRecordId(recId).ticketCode("TCK-WAR-202611-0010").build()
        ));

        var filter = new com.aquasuite.ims.dto.servicedesk.request.WarrantyRecordFilterRequest();
        filter.setPage(0);
        filter.setSize(10);
        var res = service.searchRecords(filter);

        assertThat(res.getItems()).hasSize(1);
        var item = res.getItems().get(0);
        assertThat(item.displayStatus()).isEqualTo(WarrantyDisplayStatus.CLAIMED);
        assertThat(item.canRequestSupport()).isFalse();
        assertThat(item.openTicketCode()).isEqualTo("TCK-WAR-202611-0010");
        assertThat(item.orderCode()).isEqualTo("ORD-010");
    }

    @Test
    @DisplayName("voidRecord & getRecordDetail: lý do trống, thẻ đã VOIDED, hoặc không tìm thấy thẻ -> ném lỗi đúng")
    void voidRecord_andGetDetail_ErrorGuards() {
        UUID missingId = UUID.randomUUID();
        when(warrantyRecordRepository.findById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRecordDetail(missingId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_NOT_FOUND);

        assertThatThrownBy(() -> service.voidRecord(missingId, "   "))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_VOID_REASON_REQUIRED);

        UUID voidedId = UUID.randomUUID();
        WarrantyRecord alreadyVoided = WarrantyRecord.builder()
                .warrantyRecordId(voidedId).warrantyCode("WAR-VOID").status(WarrantyStatus.VOIDED).build();
        when(warrantyRecordRepository.findById(voidedId)).thenReturn(Optional.of(alreadyVoided));
        assertThatThrownBy(() -> service.voidRecord(voidedId, "Hủy lại"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ServicedeskErrorCode.WARRANTY_ALREADY_VOIDED);
    }
}

