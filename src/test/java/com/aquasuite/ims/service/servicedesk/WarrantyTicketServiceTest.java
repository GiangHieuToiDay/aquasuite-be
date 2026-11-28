package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerAddressDto;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.sales.SalesQueryService;
import com.aquasuite.ims.event.servicedesk.WarrantyClaimSubmittedEvent;
import com.aquasuite.ims.event.servicedesk.WarrantyTicketAuditEvent;
import com.aquasuite.ims.entity.servicedesk.TicketMedia;
import com.aquasuite.ims.entity.servicedesk.WarrantyRecord;
import com.aquasuite.ims.entity.servicedesk.WarrantyTicket;
import com.aquasuite.ims.enums.servicedesk.PreferredSlot;
import com.aquasuite.ims.enums.servicedesk.WarrantyStatus;
import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import com.aquasuite.ims.exception.servicedesk.ServicedeskErrorCode;
import com.aquasuite.ims.dto.servicedesk.request.CancelWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.response.ClaimFormResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketDetailResponse;
import com.aquasuite.ims.repository.servicedesk.WarrantyRecordRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyTicketRepository;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyTicketCodeGenerator;
import com.aquasuite.ims.service.servicedesk.impl.WarrantyTicketServiceImpl;
import com.aquasuite.ims.service.servicedesk.helper.MediaValidator;
import com.aquasuite.ims.integration.servicedesk.ServicedeskMediaStoragePort;
import com.aquasuite.ims.dto.servicedesk.response.StoredMedia;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

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
class WarrantyTicketServiceTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-13T05:00:00Z"), VN_ZONE); // 2026-11-13 12:00 VN

    @Mock private WarrantyTicketRepository warrantyTicketRepository;
    @Mock private WarrantyRecordRepository warrantyRecordRepository;
    @Mock private WarrantyTicketCodeGenerator warrantyTicketCodeGenerator;
    @Mock private ServicedeskMediaStoragePort servicedeskMediaStoragePort;
    @Mock private MediaValidator mediaValidator;
    @Mock private IamQueryService iamApi;
    @Mock private CustomerService crmApi;
    @Mock private CatalogLookupService catalogApi;
    @Mock private SalesQueryService salesWarrantyApi;
    @Mock private ApplicationEventPublisher eventPublisher;

    private WarrantyTicketServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID recordId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();

    private CustomerSummaryDto customer;

    @BeforeEach
    void setUp() {
        service = new WarrantyTicketServiceImpl(
                warrantyTicketRepository,
                warrantyRecordRepository,
                warrantyTicketCodeGenerator,
                servicedeskMediaStoragePort,
                mediaValidator,
                iamApi,
                crmApi,
                catalogApi,
                salesWarrantyApi,
                eventPublisher,
                FIXED_CLOCK
        );

        customer = CustomerSummaryDto.builder()
                .customerId(customerId)
                .userId(userId)
                .customerCode("CUST-001")
                .customerName("Nguyễn Văn A")
                .phone("0987654321")
                .email("nva@example.com")
                .build();

        lenient().when(iamApi.getCurrentUserId()).thenReturn(userId);
        lenient().when(crmApi.getCustomerByUserId(userId)).thenReturn(Optional.of(customer));
        lenient().when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(
                CatalogSkuSummaryDto.builder()
                        .variantId(variantId)
                        .sku("TOTO-CS988")
                        .productName("Bồn cầu thông minh")
                        .variantName("Bản tiêu chuẩn")
                        .build()
        ));
    }

    private WarrantyRecord activeRecord(LocalDate start, LocalDate end) {
        return WarrantyRecord.builder()
                .warrantyRecordId(recordId)
                .warrantyCode("WAR-202611-0001")
                .customerId(customerId)
                .variantId(variantId)
                .serialNumber("SN-123456")
                .startDate(start)
                .endDate(end)
                .warrantyMonths(24)
                .status(WarrantyStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getClaimForm: Thành công trả về form pre-populated")
    void getClaimForm_success() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        when(warrantyRecordRepository.findById(recordId)).thenReturn(Optional.of(rec));
        when(crmApi.getDeliveryAddressesByCustomerId(customerId)).thenReturn(List.of(
                CustomerAddressDto.builder().addressId(UUID.randomUUID()).addressText("123 Lê Lợi, TP.HCM").isDefault(true).build()
        ));

        ClaimFormResponse form = service.getClaimForm(recordId);

        assertThat(form).isNotNull();
        assertThat(form.warrantyCode()).isEqualTo("WAR-202611-0001");
        assertThat(form.canSubmit()).isTrue();
        assertThat(form.expired()).isFalse();
        assertThat(form.contactName()).isEqualTo("Nguyễn Văn A");
        assertThat(form.suggestedSiteAddress()).isEqualTo("123 Lê Lợi, TP.HCM");
    }

    @Test
    @DisplayName("getClaimForm: Thẻ không thuộc khách hàng -> ném WARRANTY_FORBIDDEN (SVD_019)")
    void getClaimForm_forbidden() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        rec.setCustomerId(UUID.randomUUID()); // thuộc khách khác
        when(warrantyRecordRepository.findById(recordId)).thenReturn(Optional.of(rec));

        assertThatThrownBy(() -> service.getClaimForm(recordId))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.WARRANTY_FORBIDDEN);
    }

    @Test
    @DisplayName("getClaimForm: Thẻ VOIDED -> canSubmit=false kèm lý do")
    void getClaimForm_voided() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        rec.setStatus(WarrantyStatus.VOIDED);
        when(warrantyRecordRepository.findById(recordId)).thenReturn(Optional.of(rec));

        ClaimFormResponse form = service.getClaimForm(recordId);
        assertThat(form.canSubmit()).isFalse();
        assertThat(form.blockedReason()).contains("vô hiệu hóa");
    }

    @Test
    @DisplayName("getClaimForm: Đang có ticket mở -> canSubmit=false kèm openTicketCode")
    void getClaimForm_hasOpenTicket() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        when(warrantyRecordRepository.findById(recordId)).thenReturn(Optional.of(rec));
        when(warrantyTicketRepository.findFirstByWarrantyRecordIdAndStatusIn(eq(recordId), any()))
                .thenReturn(Optional.of(WarrantyTicket.builder()
                        .ticketId(UUID.randomUUID())
                        .ticketCode("TCK-WAR-202611-0099")
                        .status(WarrantyTicketStatus.NEW)
                        .build()));

        ClaimFormResponse form = service.getClaimForm(recordId);
        assertThat(form.canSubmit()).isFalse();
        assertThat(form.openTicketCode()).isEqualTo("TCK-WAR-202611-0099");
    }

    @Test
    @DisplayName("getClaimForm: Thẻ EXPIRED -> expired=true nhưng canSubmit=true (không chặn)")
    void getClaimForm_expired() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)); // đã hết hạn
        when(warrantyRecordRepository.findById(recordId)).thenReturn(Optional.of(rec));

        ClaimFormResponse form = service.getClaimForm(recordId);
        assertThat(form.expired()).isTrue();
        assertThat(form.canSubmit()).isTrue();
    }

    @Test
    @DisplayName("submit: Thành công tạo ticket, lưu DB và phát 2 sự kiện")
    void submit_success() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        when(warrantyRecordRepository.findByIdForUpdate(recordId)).thenReturn(Optional.of(rec));
        when(warrantyTicketRepository.findFirstByWarrantyRecordIdAndStatusIn(eq(recordId), any())).thenReturn(Optional.empty());
        when(warrantyTicketCodeGenerator.nextCode(any())).thenReturn("TCK-WAR-202611-0001");
        when(warrantyTicketRepository.saveAndFlush(any(WarrantyTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateWarrantyTicketRequest request = CreateWarrantyTicketRequest.builder()
                .warrantyRecordId(recordId)
                .issueDescription("Vòi rửa thông minh không kéo ra được khi bấm nút wash")
                .preferredDate(LocalDate.of(2026, 11, 15))
                .preferredSlot(PreferredSlot.MORNING)
                .siteAddress("Số 15 Lê Duẩn, P. Bến Nghé, Quận 1")
                .media(List.of(
                        TicketMedia.builder().type("IMAGE").url("https://res.cloudinary.com/demo/image/upload/aquasuite/warranty-tickets/" + customerId + "/img1.jpg").publicId("p1").contentType("image/jpeg").sizeBytes(1024).build()
                ))
                .build();

        WarrantyTicketDetailResponse response = service.submit(request);

        assertThat(response).isNotNull();
        assertThat(response.ticketCode()).isEqualTo("TCK-WAR-202611-0001");
        assertThat(response.status()).isEqualTo(WarrantyTicketStatus.NEW);
        assertThat(response.canCancel()).isTrue();

        // Kiểm tra 2 sự kiện được phát ra
        verify(eventPublisher).publishEvent(any(WarrantyTicketAuditEvent.class));
        verify(eventPublisher).publishEvent(any(WarrantyClaimSubmittedEvent.class));
    }

    @Test
    @DisplayName("submit: Thẻ hết hạn mà không xác nhận -> từ chối SVD_033 (C+)")
    void submit_expiredWithoutAck_rejected() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)); // đã hết hạn
        when(warrantyRecordRepository.findByIdForUpdate(recordId)).thenReturn(Optional.of(rec));

        CreateWarrantyTicketRequest request = CreateWarrantyTicketRequest.builder()
                .warrantyRecordId(recordId)
                .issueDescription("Thiết bị hỏng, thẻ đã hết hạn nhưng không tích xác nhận")
                .siteAddress("Số 15 Lê Duẩn, P. Bến Nghé, Quận 1")
                .acknowledgeExpired(false)
                .build();

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("hết hạn");
    }

    @Test
    @DisplayName("submit: Thẻ hết hạn CÓ xác nhận -> tạo ticket, lưu vết expiredAcknowledged=true (C+)")
    void submit_expiredWithAck_success() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)); // đã hết hạn
        when(warrantyRecordRepository.findByIdForUpdate(recordId)).thenReturn(Optional.of(rec));
        when(warrantyTicketRepository.findFirstByWarrantyRecordIdAndStatusIn(eq(recordId), any())).thenReturn(Optional.empty());
        when(warrantyTicketCodeGenerator.nextCode(any())).thenReturn("TCK-WAR-202611-0002");
        when(warrantyTicketRepository.saveAndFlush(any(WarrantyTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateWarrantyTicketRequest request = CreateWarrantyTicketRequest.builder()
                .warrantyRecordId(recordId)
                .issueDescription("Thiết bị hỏng, thẻ hết hạn và khách đã xác nhận vẫn gửi")
                .siteAddress("Số 15 Lê Duẩn, P. Bến Nghé, Quận 1")
                .acknowledgeExpired(true)
                .build();

        WarrantyTicketDetailResponse response = service.submit(request);

        assertThat(response.status()).isEqualTo(WarrantyTicketStatus.NEW);
        assertThat(response.warrantyExpired()).isTrue();
        assertThat(response.expiredAcknowledged()).isTrue();
    }

    @Test
    @DisplayName("submit: Thẻ VOIDED -> từ chối ném SVD_022")
    void submit_voided_rejected() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        rec.setStatus(WarrantyStatus.VOIDED);
        when(warrantyRecordRepository.findByIdForUpdate(recordId)).thenReturn(Optional.of(rec));

        CreateWarrantyTicketRequest request = CreateWarrantyTicketRequest.builder()
                .warrantyRecordId(recordId)
                .issueDescription("Mô tả lỗi hợp lệ trên mười ký tự")
                .siteAddress("Địa chỉ nhà")
                .build();

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.WARRANTY_RECORD_VOIDED);
    }

    @Test
    @DisplayName("submit: Đang có ticket mở -> từ chối ném SVD_021")
    void submit_alreadyOpenTicket_rejected() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        when(warrantyRecordRepository.findByIdForUpdate(recordId)).thenReturn(Optional.of(rec));
        when(warrantyTicketRepository.findFirstByWarrantyRecordIdAndStatusIn(eq(recordId), any()))
                .thenReturn(Optional.of(WarrantyTicket.builder().ticketCode("TCK-WAR-202611-0005").status(WarrantyTicketStatus.NEW).build()));

        CreateWarrantyTicketRequest request = CreateWarrantyTicketRequest.builder()
                .warrantyRecordId(recordId)
                .issueDescription("Mô tả lỗi hợp lệ trên mười ký tự")
                .siteAddress("Địa chỉ nhà")
                .build();

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.WARRANTY_TICKET_ALREADY_OPEN);
    }

    @Test
    @DisplayName("submit: Mô tả sự cố < 10 ký tự -> từ chối")
    void submit_shortDescription_rejected() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        when(warrantyRecordRepository.findByIdForUpdate(recordId)).thenReturn(Optional.of(rec));

        CreateWarrantyTicketRequest request = CreateWarrantyTicketRequest.builder()
                .warrantyRecordId(recordId)
                .issueDescription("Hỏng máy") // 8 ký tự
                .siteAddress("Địa chỉ nhà")
                .build();

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);
    }

    @Test
    @DisplayName("submit: Ngày hẹn trong quá khứ -> từ chối ném SVD_026")
    void submit_pastDate_rejected() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        when(warrantyRecordRepository.findByIdForUpdate(recordId)).thenReturn(Optional.of(rec));

        CreateWarrantyTicketRequest request = CreateWarrantyTicketRequest.builder()
                .warrantyRecordId(recordId)
                .issueDescription("Mô tả sự cố chi tiết đầy đủ hợp lệ")
                .preferredDate(LocalDate.of(2026, 11, 12)) // hôm qua
                .siteAddress("Địa chỉ nhà")
                .build();

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_PREFERRED_DATE);
    }

    @Test
    @DisplayName("submit: Ngày hẹn quá 60 ngày -> từ chối ném SVD_026")
    void submit_farFutureDate_rejected() {
        WarrantyRecord rec = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        when(warrantyRecordRepository.findByIdForUpdate(recordId)).thenReturn(Optional.of(rec));

        CreateWarrantyTicketRequest request = CreateWarrantyTicketRequest.builder()
                .warrantyRecordId(recordId)
                .issueDescription("Mô tả sự cố chi tiết đầy đủ hợp lệ")
                .preferredDate(LocalDate.of(2027, 2, 1)) // quá 60 ngày
                .siteAddress("Địa chỉ nhà")
                .build();

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_PREFERRED_DATE);
    }

    @Test
    @DisplayName("cancel: Ticket NEW -> Hủy thành công và chuyển trạng thái CANCELLED")
    void cancel_success() {
        UUID ticketId = UUID.randomUUID();
        WarrantyTicket ticket = WarrantyTicket.builder()
                .ticketId(ticketId)
                .ticketCode("TCK-WAR-202611-0001")
                .warrantyRecordId(recordId)
                .customerId(customerId)
                .status(WarrantyTicketStatus.NEW)
                .build();

        when(warrantyTicketRepository.findByTicketIdAndCustomerId(ticketId, customerId)).thenReturn(Optional.of(ticket));
        when(warrantyTicketRepository.save(any(WarrantyTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        WarrantyTicketDetailResponse response = service.cancel(ticketId, new CancelWarrantyTicketRequest("Tôi đã tự sửa được rồi"));

        assertThat(response.status()).isEqualTo(WarrantyTicketStatus.CANCELLED);
        assertThat(response.cancelReason()).isEqualTo("Tôi đã tự sửa được rồi");
        verify(eventPublisher).publishEvent(any(WarrantyTicketAuditEvent.class));
    }

    @Test
    @DisplayName("cancel: Ticket đã IN_PROGRESS -> từ chối ném SVD_028")
    void cancel_inProgress_rejected() {
        UUID ticketId = UUID.randomUUID();
        WarrantyTicket ticket = WarrantyTicket.builder()
                .ticketId(ticketId)
                .ticketCode("TCK-WAR-202611-0001")
                .warrantyRecordId(recordId)
                .customerId(customerId)
                .status(WarrantyTicketStatus.IN_PROGRESS)
                .build();

        when(warrantyTicketRepository.findByTicketIdAndCustomerId(ticketId, customerId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.cancel(ticketId, new CancelWarrantyTicketRequest("Hủy")))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.WARRANTY_TICKET_NOT_CANCELLABLE);
    }

    @Test
    @DisplayName("uploadMedia: định tuyến ảnh sang uploadImage, video sang uploadVideo, từ chối file rỗng hoặc PDF")
    void uploadMedia_RoutingAndValidation() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> service.uploadMedia(emptyFile))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);

        MockMultipartFile pdfFile = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[128]);
        assertThatThrownBy(() -> service.uploadMedia(pdfFile))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);

        MockMultipartFile imgFile = new MockMultipartFile("file", "img.jpg", "image/jpeg", new byte[128]);
        StoredMedia imgRes = new StoredMedia("IMAGE", "https://cdn/img.jpg", "p-img", "image/jpeg", 128);
        when(servicedeskMediaStoragePort.uploadImage(imgFile, customerId)).thenReturn(imgRes);
        assertThat(service.uploadMedia(imgFile)).isEqualTo(imgRes);

        MockMultipartFile vidFile = new MockMultipartFile("file", "vid.mp4", "video/mp4", new byte[256]);
        StoredMedia vidRes = new StoredMedia("VIDEO", "https://cdn/vid.mp4", "p-vid", "video/mp4", 256);
        when(servicedeskMediaStoragePort.uploadVideo(vidFile, customerId)).thenReturn(vidRes);
        assertThat(service.uploadMedia(vidFile)).isEqualTo(vidRes);
    }

    @Test
    @DisplayName("getClaimForm: ưu tiên địa chỉ giao hàng từ đơn gốc khi thẻ gắn orderId")
    void getClaimForm_PrefersOrderDeliveryAddress() {
        UUID orderId = UUID.randomUUID();
        WarrantyRecord rec = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        rec.setOrderId(orderId);
        when(warrantyRecordRepository.findById(recordId)).thenReturn(Optional.of(rec));
        when(salesWarrantyApi.getOrderSummaryWithInvoice(orderId)).thenReturn(Optional.of(
                com.aquasuite.ims.dto.sales.response.SalesOrderWarrantySummaryDto.builder()
                        .orderId(orderId)
                        .deliveryAddress("  45 Nguyễn Huệ, Q1  ")
                        .build()
        ));
        when(crmApi.getDeliveryAddressesByCustomerId(customerId)).thenReturn(List.of(
                CustomerAddressDto.builder().addressId(UUID.randomUUID()).addressText("Địa chỉ phụ CRM").build()
        ));

        ClaimFormResponse form = service.getClaimForm(recordId);

        assertThat(form.suggestedSiteAddress()).isEqualTo("45 Nguyễn Huệ, Q1");
        assertThat(form.customerAddresses()).containsExactly("Địa chỉ phụ CRM");
    }

    @Test
    @DisplayName("submit: thẻ không tồn tại hoặc thuộc khách hàng khác -> ném WARRANTY_NOT_FOUND / WARRANTY_FORBIDDEN")
    void submit_NotFoundOrForbidden() {
        UUID missingRecord = UUID.randomUUID();
        when(warrantyRecordRepository.findByIdForUpdate(missingRecord)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(CreateWarrantyTicketRequest.builder()
                .warrantyRecordId(missingRecord)
                .issueDescription("Mô tả chi tiết lỗi đủ 10 ký tự")
                .siteAddress("123 Đường A")
                .build()))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.WARRANTY_NOT_FOUND);

        WarrantyRecord otherCustomerRecord = activeRecord(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        otherCustomerRecord.setCustomerId(UUID.randomUUID());
        when(warrantyRecordRepository.findByIdForUpdate(recordId)).thenReturn(Optional.of(otherCustomerRecord));
        assertThatThrownBy(() -> service.submit(CreateWarrantyTicketRequest.builder()
                .warrantyRecordId(recordId)
                .issueDescription("Mô tả chi tiết lỗi đủ 10 ký tự")
                .siteAddress("123 Đường A")
                .build()))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.WARRANTY_FORBIDDEN);
    }
}

