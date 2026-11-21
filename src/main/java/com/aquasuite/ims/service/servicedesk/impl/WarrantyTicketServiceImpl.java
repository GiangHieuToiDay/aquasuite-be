package com.aquasuite.ims.service.servicedesk.impl;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerAddressDto;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.sales.SalesWarrantyService;
import com.aquasuite.ims.dto.sales.response.SalesOrderWarrantySummaryDto;
import com.aquasuite.ims.event.servicedesk.WarrantyClaimSubmittedEvent;
import com.aquasuite.ims.event.servicedesk.WarrantyTicketAuditEvent;
import com.aquasuite.ims.entity.servicedesk.TechnicalJob;
import com.aquasuite.ims.entity.servicedesk.TicketMedia;
import com.aquasuite.ims.entity.servicedesk.WarrantyRecord;
import com.aquasuite.ims.entity.servicedesk.WarrantyTicket;
import com.aquasuite.ims.enums.servicedesk.TechnicalJobStatus;
import com.aquasuite.ims.enums.servicedesk.TechnicalJobType;
import com.aquasuite.ims.enums.servicedesk.WarrantyStatus;
import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import com.aquasuite.ims.exception.servicedesk.ServicedeskErrorCode;
import com.aquasuite.ims.dto.servicedesk.request.CancelWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyTicketFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.ClaimFormResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketListItemResponse;
import com.aquasuite.ims.repository.servicedesk.TechnicalJobRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyRecordRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyTicketRepository;
import com.aquasuite.ims.service.servicedesk.WarrantyTicketService;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyTicketCodeGenerator;
import com.aquasuite.ims.service.servicedesk.helper.MediaValidator;
import com.aquasuite.ims.integration.servicedesk.ServicedeskMediaStoragePort;
import com.aquasuite.ims.dto.servicedesk.response.StoredMedia;
import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.exception.AppException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarrantyTicketServiceImpl implements WarrantyTicketService {

    private static final Set<TechnicalJobStatus> OPEN_REPAIR_JOB_STATUSES = Set.of(
            TechnicalJobStatus.ASSIGNED,
            TechnicalJobStatus.IN_PROGRESS
    );

    private static final List<WarrantyTicketStatus> OPEN_TICKET_STATUSES = List.of(
            WarrantyTicketStatus.NEW,
            WarrantyTicketStatus.IN_PROGRESS
    );

    private final WarrantyTicketRepository warrantyTicketRepository;
    private final WarrantyRecordRepository warrantyRecordRepository;
    private final TechnicalJobRepository technicalJobRepository;
    private final WarrantyTicketCodeGenerator warrantyTicketCodeGenerator;
    private final ServicedeskMediaStoragePort servicedeskMediaStoragePort;
    private final MediaValidator mediaValidator;
    private final IamQueryService iamApi;
    private final CustomerService crmApi;
    private final CatalogLookupService catalogApi;
    private final SalesWarrantyService salesWarrantyApi;
    private final ApplicationEventPublisher eventPublisher;

    @Qualifier("servicedeskClock")
    private final Clock servicedeskClock;

    @Override
    @Transactional(readOnly = true)
    public ClaimFormResponse getClaimForm(UUID warrantyRecordId) {
        CustomerSummaryDto customer = currentCustomer();
        WarrantyRecord record = warrantyRecordRepository.findById(warrantyRecordId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_NOT_FOUND, warrantyRecordId));

        if (!Objects.equals(record.getCustomerId(), customer.customerId())) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_FORBIDDEN);
        }

        LocalDate today = LocalDate.now(servicedeskClock);
        boolean expired = record.getEndDate() != null && record.getEndDate().isBefore(today);

        // Kiểm tra các điều kiện chặn gửi yêu cầu
        boolean hasOpenJob = technicalJobRepository.findByWarrantyRecordIdOrderByCreatedAtDesc(warrantyRecordId).stream()
                .anyMatch(j -> j.getJobType() == TechnicalJobType.WARRANTY_REPAIR && OPEN_REPAIR_JOB_STATUSES.contains(j.getStatus()));

        Optional<WarrantyTicket> openTicketOpt = warrantyTicketRepository.findFirstByWarrantyRecordIdAndStatusIn(
                warrantyRecordId, OPEN_TICKET_STATUSES);

        boolean canSubmit = true;
        String blockedReason = null;
        String openTicketCode = openTicketOpt.map(WarrantyTicket::getTicketCode).orElse(null);

        if (record.getStatus() == WarrantyStatus.VOIDED) {
            canSubmit = false;
            blockedReason = "Thẻ bảo hành đã bị vô hiệu hóa.";
        } else if (hasOpenJob) {
            canSubmit = false;
            blockedReason = "Thiết bị đang có phiếu sửa chữa bảo hành đang mở.";
        } else if (openTicketCode != null) {
            canSubmit = false;
            blockedReason = "Đang có yêu cầu bảo hành mở: " + openTicketCode;
        }

        CatalogSkuSummaryDto sku = catalogApi.getSkuSummary(record.getVariantId()).orElse(null);

        // Lấy địa chỉ gợi ý từ đơn hàng hoặc fallback sổ địa chỉ CRM
        List<CustomerAddressDto> addresses = crmApi.getDeliveryAddressesByCustomerId(customer.customerId());
        SalesOrderWarrantySummaryDto orderSummary = record.getOrderId() != null
                ? salesWarrantyApi.getOrderSummaryWithInvoice(record.getOrderId()).orElse(null)
                : null;

        String suggestedAddress = orderSummary != null && StringUtils.hasText(orderSummary.deliveryAddress())
                ? orderSummary.deliveryAddress().trim()
                : addresses.stream()
                        .findFirst()
                        .map(CustomerAddressDto::addressText)
                        .orElse(null);

        List<String> customerAddressTexts = addresses.stream()
                .map(CustomerAddressDto::addressText)
                .filter(StringUtils::hasText)
                .toList();

        return ClaimFormResponse.builder()
                .warrantyRecordId(record.getWarrantyRecordId())
                .warrantyCode(record.getWarrantyCode())
                .productName(sku != null ? sku.productName() : null)
                .variantName(sku != null ? sku.variantName() : null)
                .sku(sku != null ? sku.sku() : null)
                .serialNumber(record.getSerialNumber())
                .startDate(record.getStartDate())
                .endDate(record.getEndDate())
                .expired(expired)
                .canSubmit(canSubmit)
                .blockedReason(blockedReason)
                .openTicketCode(openTicketCode)
                .contactName(customer.customerName())
                .contactPhone(customer.phone())
                .contactEmail(customer.email())
                .suggestedSiteAddress(suggestedAddress)
                .customerAddresses(customerAddressTexts)
                .build();
    }

    @Override
    public StoredMedia uploadMedia(MultipartFile file) {
        CustomerSummaryDto customer = currentCustomer();
        if (file == null || file.isEmpty()) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "File rỗng hoặc không tồn tại.");
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "Không xác định được định dạng file.");
        }
        String lowerType = contentType.toLowerCase(Locale.ROOT);
        if (lowerType.startsWith("image/")) {
            return servicedeskMediaStoragePort.uploadImage(file, customer.customerId());
        } else if (lowerType.startsWith("video/")) {
            return servicedeskMediaStoragePort.uploadVideo(file, customer.customerId());
        } else {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "Định dạng file không được hỗ trợ (chỉ nhận ảnh hoặc video).");
        }
    }

    @Override
    @Transactional
    public WarrantyTicketDetailResponse submit(CreateWarrantyTicketRequest request) {
        CustomerSummaryDto customer = currentCustomer();

        // 1. Khóa bi quan bản ghi thẻ bảo hành để chống tạo song song
        WarrantyRecord record = warrantyRecordRepository.findByIdForUpdate(request.warrantyRecordId())
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_NOT_FOUND, request.warrantyRecordId()));

        // 2. Kiểm tra quyền sở hữu
        if (!Objects.equals(record.getCustomerId(), customer.customerId())) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_FORBIDDEN);
        }

        // 3. Thẻ bị VOIDED không được gửi yêu cầu
        if (record.getStatus() == WarrantyStatus.VOIDED) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_RECORD_VOIDED);
        }

        // 3b. Thẻ đã hết hạn: cho phép gửi nhưng khách phải chủ động xác nhận (C+). Lưu vết đã xác nhận.
        boolean expired = record.getEndDate() != null
                && record.getEndDate().isBefore(LocalDate.now(servicedeskClock));
        if (expired && !request.acknowledgeExpired()) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_RECORD_EXPIRED_NOT_ACKNOWLEDGED);
        }

        // 4. Kiểm tra có ticket mở hoặc job bảo hành mở
        Optional<WarrantyTicket> openTicket = warrantyTicketRepository.findFirstByWarrantyRecordIdAndStatusIn(
                record.getWarrantyRecordId(), OPEN_TICKET_STATUSES);
        if (openTicket.isPresent()) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_TICKET_ALREADY_OPEN, openTicket.get().getTicketCode());
        }

        boolean hasOpenJob = technicalJobRepository.findByWarrantyRecordIdOrderByCreatedAtDesc(record.getWarrantyRecordId()).stream()
                .anyMatch(j -> j.getJobType() == TechnicalJobType.WARRANTY_REPAIR && OPEN_REPAIR_JOB_STATUSES.contains(j.getStatus()));
        if (hasOpenJob) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_TICKET_ALREADY_OPEN, "JOB_IN_PROGRESS");
        }

        // 5. Validate dữ liệu yêu cầu
        String description = request.issueDescription() != null ? request.issueDescription().trim() : "";
        if (description.length() < 10 || description.length() > 2000) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "Mô tả sự cố phải từ 10 đến 2000 ký tự.");
        }

        LocalDate today = LocalDate.now(servicedeskClock);
        if (request.preferredDate() != null) {
            if (request.preferredDate().isBefore(today) || request.preferredDate().isAfter(today.plusDays(60))) {
                throw new AppException(ServicedeskErrorCode.INVALID_PREFERRED_DATE);
            }
        }

        String siteAddress = request.siteAddress() != null ? request.siteAddress().trim() : "";
        if (!StringUtils.hasText(siteAddress)) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "Địa chỉ xử lý là bắt buộc.");
        }

        mediaValidator.validateMediaList(request.media(), customer.customerId());

        // 6. Sinh mã ticket tự động TCK-WAR-YYYYMM-XXXX
        String ticketCode = warrantyTicketCodeGenerator.nextCode(today);

        // 7. Tạo và lưu entity WarrantyTicket
        WarrantyTicket ticket = WarrantyTicket.submit(
                ticketCode,
                record.getWarrantyRecordId(),
                customer.customerId(),
                description,
                request.preferredDate(),
                request.preferredSlot(),
                siteAddress,
                customer.customerName(),
                customer.phone(),
                customer.email(),
                request.media(),
                expired
        );

        WarrantyTicket saved;
        try {
            saved = warrantyTicketRepository.saveAndFlush(ticket);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent ticket creation blocked by unique index for recordId={}", record.getWarrantyRecordId());
            throw new AppException(ServicedeskErrorCode.WARRANTY_TICKET_ALREADY_OPEN, ticketCode);
        }

        // 8. Bắn sự kiện kiểm toán Audit Log
        eventPublisher.publishEvent(new WarrantyTicketAuditEvent(
                UUID.randomUUID(),
                saved.getTicketId(),
                customer.userId() != null ? customer.userId() : customer.customerId(),
                "CREATED",
                null,
                Map.of(
                        "ticketCode", saved.getTicketCode(),
                        "warrantyRecordId", saved.getWarrantyRecordId(),
                        "status", saved.getStatus().name()
                ),
                saved.getCreatedAt()
        ));

        // 9. Bắn Domain Event cho toàn hệ thống
        List<String> mediaUrls = saved.getMedia() != null
                ? saved.getMedia().stream().map(TicketMedia::url).toList()
                : List.of();
        eventPublisher.publishEvent(new WarrantyClaimSubmittedEvent(
                UUID.randomUUID(),
                saved.getTicketId(),
                saved.getTicketCode(),
                saved.getWarrantyRecordId(),
                saved.getCustomerId(),
                saved.getIssueDescription(),
                saved.getSiteAddress(),
                saved.getPreferredDate(),
                saved.getPreferredSlot() != null ? saved.getPreferredSlot().name() : null,
                saved.getContactName(),
                saved.getContactPhone(),
                mediaUrls,
                saved.getCreatedAt()
        ));

        log.info("Khách hàng {} đã gửi thành công yêu cầu bảo hành {} cho thẻ {}",
                customer.customerCode(), saved.getTicketCode(), record.getWarrantyCode());
        return toDetailResponse(saved, record);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WarrantyTicketListItemResponse> listMine(WarrantyTicketFilterRequest filter) {
        CustomerSummaryDto customer = currentCustomer();
        int page = filter != null && filter.getPage() != null && filter.getPage() >= 0 ? filter.getPage() : 0;
        int size = filter != null && filter.getSize() != null && filter.getSize() > 0 ? filter.getSize() : 20;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<WarrantyTicket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customer.customerId()));
            if (filter != null && filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<WarrantyTicket> ticketPage = warrantyTicketRepository.findAll(spec, pageable);
        List<WarrantyTicket> tickets = ticketPage.getContent();

        Set<UUID> recordIds = tickets.stream().map(WarrantyTicket::getWarrantyRecordId).collect(Collectors.toSet());
        Map<UUID, WarrantyRecord> recordMap = warrantyRecordRepository.findAllById(recordIds).stream()
                .collect(Collectors.toMap(WarrantyRecord::getWarrantyRecordId, r -> r));

        Set<UUID> variantIds = recordMap.values().stream().map(WarrantyRecord::getVariantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, CatalogSkuSummaryDto> skuMap = catalogApi.getSkuSummaries(variantIds);

        List<WarrantyTicketListItemResponse> items = tickets.stream().map(t -> {
            WarrantyRecord rec = recordMap.get(t.getWarrantyRecordId());
            CatalogSkuSummaryDto sku = (rec != null && rec.getVariantId() != null) ? skuMap.get(rec.getVariantId()) : null;
            return WarrantyTicketListItemResponse.builder()
                    .ticketId(t.getTicketId())
                    .ticketCode(t.getTicketCode())
                    .warrantyRecordId(t.getWarrantyRecordId())
                    .warrantyCode(rec != null ? rec.getWarrantyCode() : null)
                    .productName(sku != null ? sku.productName() : null)
                    .variantName(sku != null ? sku.variantName() : null)
                    .serialNumber(rec != null ? rec.getSerialNumber() : null)
                    .issueDescription(t.getIssueDescription())
                    .preferredDate(t.getPreferredDate())
                    .preferredSlot(t.getPreferredSlot())
                    .siteAddress(t.getSiteAddress())
                    .status(t.getStatus())
                    .canCancel(t.getStatus() == WarrantyTicketStatus.NEW)
                    .mediaCount(t.getMedia() != null ? t.getMedia().size() : 0)
                    .createdAt(t.getCreatedAt())
                    .build();
        }).toList();

        return PageResponse.of(items, ticketPage.getNumber(), ticketPage.getSize(), ticketPage.getTotalElements(), ticketPage.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public WarrantyTicketDetailResponse getMine(UUID ticketId) {
        CustomerSummaryDto customer = currentCustomer();
        WarrantyTicket ticket = warrantyTicketRepository.findByTicketIdAndCustomerId(ticketId, customer.customerId())
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_TICKET_NOT_FOUND, ticketId));

        WarrantyRecord record = warrantyRecordRepository.findById(ticket.getWarrantyRecordId()).orElse(null);
        return toDetailResponse(ticket, record);
    }

    @Override
    @Transactional
    public WarrantyTicketDetailResponse cancel(UUID ticketId, CancelWarrantyTicketRequest request) {
        CustomerSummaryDto customer = currentCustomer();
        WarrantyTicket ticket = warrantyTicketRepository.findByTicketIdAndCustomerId(ticketId, customer.customerId())
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_TICKET_FORBIDDEN, ticketId));

        OffsetDateTime now = OffsetDateTime.now();
        ticket.cancel(request != null ? request.reason() : null, now);
        WarrantyTicket saved = warrantyTicketRepository.save(ticket);

        eventPublisher.publishEvent(new WarrantyTicketAuditEvent(
                UUID.randomUUID(),
                saved.getTicketId(),
                customer.userId() != null ? customer.userId() : customer.customerId(),
                "CANCELLED",
                Map.of("status", "NEW"),
                Map.of(
                        "status", "CANCELLED",
                        "cancelReason", saved.getCancelReason() != null ? saved.getCancelReason() : ""
                ),
                now
        ));

        WarrantyRecord record = warrantyRecordRepository.findById(saved.getWarrantyRecordId()).orElse(null);
        log.info("Khách hàng {} đã hủy thành công yêu cầu bảo hành {}", customer.customerCode(), saved.getTicketCode());
        return toDetailResponse(saved, record);
    }

    private CustomerSummaryDto currentCustomer() {
        UUID currentUserId = iamApi.getCurrentUserId();
        return crmApi.getCustomerByUserId(currentUserId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.CUSTOMER_NOT_FOUND, currentUserId));
    }

    private WarrantyTicketDetailResponse toDetailResponse(WarrantyTicket ticket, WarrantyRecord record) {
        CatalogSkuSummaryDto sku = (record != null && record.getVariantId() != null)
                ? catalogApi.getSkuSummary(record.getVariantId()).orElse(null)
                : null;

        boolean expired = record != null && record.getEndDate() != null
                && record.getEndDate().isBefore(LocalDate.now(servicedeskClock));

        return WarrantyTicketDetailResponse.builder()
                .ticketId(ticket.getTicketId())
                .ticketCode(ticket.getTicketCode())
                .warrantyRecordId(ticket.getWarrantyRecordId())
                .warrantyCode(record != null ? record.getWarrantyCode() : null)
                .productName(sku != null ? sku.productName() : null)
                .variantName(sku != null ? sku.variantName() : null)
                .sku(sku != null ? sku.sku() : null)
                .serialNumber(record != null ? record.getSerialNumber() : null)
                .warrantyStartDate(record != null ? record.getStartDate() : null)
                .warrantyEndDate(record != null ? record.getEndDate() : null)
                .warrantyExpired(expired)
                .expiredAcknowledged(ticket.isExpiredAcknowledged())
                .contactName(ticket.getContactName())
                .contactPhone(ticket.getContactPhone())
                .contactEmail(ticket.getContactEmail())
                .issueDescription(ticket.getIssueDescription())
                .preferredDate(ticket.getPreferredDate())
                .preferredSlot(ticket.getPreferredSlot())
                .siteAddress(ticket.getSiteAddress())
                .media(ticket.getMedia() != null ? ticket.getMedia() : List.of())
                .status(ticket.getStatus())
                .canCancel(ticket.getStatus() == WarrantyTicketStatus.NEW)
                .technicalJobId(ticket.getTechnicalJobId())
                .result(ticket.getResult())
                .rejectionReason(ticket.getRejectionReason())
                .cancelReason(ticket.getCancelReason())
                .completedAt(ticket.getCompletedAt())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }
}

