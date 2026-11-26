package com.aquasuite.ims.service.servicedesk.impl;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.service.catalog.CatalogWarrantyLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerAddressDto;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.sales.SalesQueryService;
import com.aquasuite.ims.dto.sales.response.SalesOrderItemWarrantySnapshotDto;
import com.aquasuite.ims.dto.sales.response.SalesOrderWarrantySummaryDto;
import com.aquasuite.ims.event.servicedesk.WarrantyRecordAuditEvent;
import com.aquasuite.ims.entity.servicedesk.WarrantyRecord;
import com.aquasuite.ims.entity.servicedesk.WarrantyTicket;
import com.aquasuite.ims.enums.servicedesk.WarrantyDisplayStatus;
import com.aquasuite.ims.enums.servicedesk.WarrantyStatus;
import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import com.aquasuite.ims.exception.servicedesk.ServicedeskErrorCode;
import com.aquasuite.ims.dto.servicedesk.request.CreateAdHocWarrantyRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateDeliveryWarrantyCommand;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyFromOrderRequest;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyRecordFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicySummaryResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordListItemResponse;
import com.aquasuite.ims.integration.servicedesk.FulfillmentWarrantyPort;
import com.aquasuite.ims.repository.servicedesk.WarrantyRecordRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyTicketRepository;
import com.aquasuite.ims.service.servicedesk.WarrantyRecordService;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyCodeGenerator;
import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.util.PageableUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.aquasuite.ims.dto.sales.response.SalesOrderItemSummaryDto;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyLookupResponse;
import com.aquasuite.ims.service.sales.SalesQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@Service
public class WarrantyRecordServiceImpl implements WarrantyRecordService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "startDate", "endDate", "warrantyCode", "status"
    );
    private static final List<WarrantyTicketStatus> OPEN_TICKET_STATUSES =
            List.of(WarrantyTicketStatus.NEW, WarrantyTicketStatus.IN_PROGRESS);

    private final WarrantyRecordRepository warrantyRecordRepository;
    private final WarrantyTicketRepository warrantyTicketRepository;
    private final WarrantyCodeGenerator warrantyCodeGenerator;
    private final CatalogLookupService catalogApi;
    private final CatalogWarrantyLookupService catalogWarrantyApi;
    private final CustomerService crmApi;
    private final IamQueryService iamApi;
    private final FulfillmentWarrantyPort fulfillmentWarrantyPort;
    private final ApplicationEventPublisher eventPublisher;

    @Qualifier("servicedeskClock")
    private final Clock servicedeskClock;
    private final SalesQueryService salesQueryService;

    @Autowired
    public WarrantyRecordServiceImpl(
            WarrantyRecordRepository warrantyRecordRepository,
            WarrantyTicketRepository warrantyTicketRepository,
            WarrantyCodeGenerator warrantyCodeGenerator,
            CatalogLookupService catalogApi,
            CatalogWarrantyLookupService catalogWarrantyApi,
            CustomerService crmApi,
            IamQueryService iamApi,
            FulfillmentWarrantyPort fulfillmentWarrantyPort,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("servicedeskClock") Clock servicedeskClock,
            SalesQueryService salesQueryService) {
        this.warrantyRecordRepository = warrantyRecordRepository;
        this.warrantyTicketRepository = warrantyTicketRepository;
        this.warrantyCodeGenerator = warrantyCodeGenerator;
        this.catalogApi = catalogApi;
        this.catalogWarrantyApi = catalogWarrantyApi;
        this.crmApi = crmApi;
        this.iamApi = iamApi;
        this.fulfillmentWarrantyPort = fulfillmentWarrantyPort;
        this.eventPublisher = eventPublisher;
        this.servicedeskClock = servicedeskClock;
        this.salesQueryService = salesQueryService;
    }

    public WarrantyRecordServiceImpl(
            WarrantyRecordRepository warrantyRecordRepository,
            IamQueryService iamApi,
            CustomerService crmApi,
            CatalogLookupService catalogApi,
            SalesQueryService salesQueryService,
            Clock clock) {
        this(warrantyRecordRepository, null, null, catalogApi, null, crmApi, iamApi, null, null, clock, salesQueryService);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WarrantyRecordListItemResponse> searchRecords(WarrantyRecordFilterRequest filter) {
        WarrantyRecordFilterRequest safeFilter = filter != null ? filter : new WarrantyRecordFilterRequest();
        Pageable pageable = PageableUtils.createPageable(
                safeFilter.getPage(),
                safeFilter.getSize(),
                safeFilter.getSortBy(),
                safeFilter.getDirection(),
                ALLOWED_SORT_FIELDS,
                "createdAt",
                Sort.Direction.DESC
        );

        List<UUID> phoneCustomerIds = null;
        if (safeFilter.getPhone() != null && !safeFilter.getPhone().isBlank()) {
            phoneCustomerIds = crmApi.findCustomerIdsByPhoneKeyword(safeFilter.getPhone());
            if (phoneCustomerIds.isEmpty()) {
                return PageResponse.of(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0L, 0);
            }
        }

        LocalDate today = LocalDate.now(servicedeskClock);
        Specification<WarrantyRecord> spec = buildSpec(safeFilter, phoneCustomerIds, today);
        Page<WarrantyRecord> page = warrantyRecordRepository.findAll(spec, pageable);
        return toPageResponse(page, today);
    }

    @Override
    @Transactional(readOnly = true)
    public WarrantyRecordDetailResponse getRecordDetail(UUID warrantyRecordId) {
        WarrantyRecord record = warrantyRecordRepository.findById(warrantyRecordId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_NOT_FOUND, warrantyRecordId));
        return buildDetailResponse(record);
    }

    @Override
    @Transactional
    public WarrantyRecordDetailResponse createFromOrder(CreateWarrantyFromOrderRequest request) {
        validateWarrantyStartDate(request.startDate());
        SalesOrderItemWarrantySnapshotDto snapshot = salesQueryService.lockOrderItemForWarranty(request.orderItemId())
                .orElseThrow(() -> new AppException(
                        ServicedeskErrorCode.WARRANTY_ORDER_ITEM_NOT_FOUND, request.orderItemId()));

        if ("CANCELLED".equalsIgnoreCase(snapshot.orderStatus()) || "DRAFT".equalsIgnoreCase(snapshot.orderStatus())) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_ORDER_NOT_ELIGIBLE, snapshot.orderStatus());
        }

        CatalogWarrantyPolicyDto variantPolicy = catalogWarrantyApi.getWarrantyInfoByVariantId(snapshot.variantId())
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_VARIANT_NOT_FOUND, snapshot.variantId()));

        assertPolicyEligible(variantPolicy);

        String normalizedSerial = normalizeSerial(request.serialNumber());
        validateSerialRule(variantPolicy, normalizedSerial);

        int maxAllowed = snapshot.quantity() != null ? snapshot.quantity().intValue() : 1;
        if (maxAllowed <= 0) {
            maxAllowed = 1;
        }
        long existingActiveCount = warrantyRecordRepository.countByOrderItemIdAndStatus(
                snapshot.orderItemId(), WarrantyStatus.ACTIVE);
        if (existingActiveCount >= maxAllowed) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_QUANTITY_LIMIT_EXCEEDED);
        }

        UUID actorId = iamApi.getCurrentUserId();
        WarrantyRecord saved = persistNewRecord(
                snapshot.customerId(),
                snapshot.orderId(),
                snapshot.orderItemId(),
                null,
                snapshot.variantId(),
                variantPolicy,
                normalizedSerial,
                request.startDate(),
                actorId,
                "CREATE_FROM_ORDER"
        );
        return buildDetailResponse(saved);
    }

    @Override
    @Transactional
    public WarrantyRecordDetailResponse createAdHoc(CreateAdHocWarrantyRequest request) {
        validateWarrantyStartDate(request.startDate());
        crmApi.getCustomerById(request.customerId())
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.CUSTOMER_NOT_FOUND, request.customerId()));

        CatalogWarrantyPolicyDto variantInfo = catalogWarrantyApi.getWarrantyInfoByVariantId(request.variantId())
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_VARIANT_NOT_FOUND, request.variantId()));

        CatalogWarrantyPolicyDto resolvedPolicy = resolveAdHocPolicy(variantInfo, request.warrantyPolicyId());
        assertPolicyEligible(resolvedPolicy);

        String normalizedSerial = normalizeSerial(request.serialNumber());
        validateSerialRule(variantInfo, normalizedSerial);

        UUID actorId = iamApi.getCurrentUserId();
        WarrantyRecord saved = persistNewRecord(
                request.customerId(),
                null,
                null,
                null,
                request.variantId(),
                resolvedPolicy,
                normalizedSerial,
                request.startDate(),
                actorId,
                "CREATE_AD_HOC"
        );
        return buildDetailResponse(saved);
    }

    private void validateWarrantyStartDate(LocalDate startDate) {
        if (startDate == null) {
            return;
        }
        Clock clock = servicedeskClock != null ? servicedeskClock : Clock.systemDefaultZone();
        LocalDate today = LocalDate.now(clock);
        if (startDate.isAfter(today) || startDate.isBefore(LocalDate.of(2000, 1, 1))) {
            throw new AppException(
                    com.aquasuite.ims.exception.CommonErrorCode.VALIDATION_ERROR,
                    "Ngày kích hoạt bảo hành phải từ 01/01/2000 đến hiện tại.");
        }
    }

    @Override
    @Transactional
    public WarrantyRecordDetailResponse createFromDelivery(CreateDeliveryWarrantyCommand command) {
        SalesOrderItemWarrantySnapshotDto snapshot = salesQueryService.lockOrderItemForWarranty(command.orderItemId())
                .orElseThrow(() -> new AppException(
                        ServicedeskErrorCode.WARRANTY_ORDER_ITEM_NOT_FOUND, command.orderItemId()));

        if ("CANCELLED".equalsIgnoreCase(snapshot.orderStatus()) || "DRAFT".equalsIgnoreCase(snapshot.orderStatus())) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_ORDER_NOT_ELIGIBLE, snapshot.orderStatus());
        }

        String normalizedSerial = normalizeSerial(command.serialNumber());

        // Idempotent pre-check for repeated delivery events
        if (normalizedSerial != null) {
            Optional<WarrantyRecord> existingSerial = warrantyRecordRepository
                    .findFirstByVariantIdAndSerialNumberIgnoreCaseAndStatus(
                            snapshot.variantId(), normalizedSerial, WarrantyStatus.ACTIVE);
            if (existingSerial.isPresent()) {
                WarrantyRecord found = existingSerial.get();
                if (Objects.equals(found.getOrderItemId(), command.orderItemId())
                        && Objects.equals(found.getDeliveryId(), command.deliveryId())) {
                    return buildDetailResponse(found);
                }
                throw new AppException(ServicedeskErrorCode.WARRANTY_DUPLICATE_ACTIVE_SERIAL, normalizedSerial);
            }
        } else if (command.deliveryId() != null) {
            Optional<WarrantyRecord> existingNoSerial = warrantyRecordRepository
                    .findFirstByOrderItemIdAndDeliveryIdAndSerialNumberIsNullAndStatus(
                            command.orderItemId(), command.deliveryId(), WarrantyStatus.ACTIVE);
            if (existingNoSerial.isPresent()) {
                return buildDetailResponse(existingNoSerial.get());
            }
        }

        CatalogWarrantyPolicyDto variantPolicy = catalogWarrantyApi.getWarrantyInfoByVariantId(snapshot.variantId())
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_VARIANT_NOT_FOUND, snapshot.variantId()));
        assertPolicyEligible(variantPolicy);

        if (Boolean.TRUE.equals(variantPolicy.serialTracking()) && normalizedSerial == null) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_SERIAL_REQUIRED);
        }

        LocalDate activationDate = command.deliveryDate() != null
                ? command.deliveryDate()
                : LocalDate.now(servicedeskClock);

        try {
            WarrantyRecord saved = persistNewRecord(
                    snapshot.customerId(),
                    snapshot.orderId(),
                    snapshot.orderItemId(),
                    command.deliveryId(),
                    snapshot.variantId(),
                    variantPolicy,
                    normalizedSerial,
                    activationDate,
                    command.actorId(),
                    "CREATE_FROM_DELIVERY"
            );
            return buildDetailResponse(saved);
        } catch (AppException ex) {
            if (ex.getErrorCode() == ServicedeskErrorCode.WARRANTY_DUPLICATE_ACTIVE_SERIAL && command.deliveryId() != null) {
                if (normalizedSerial != null) {
                    Optional<WarrantyRecord> retry = warrantyRecordRepository
                            .findFirstByVariantIdAndSerialNumberIgnoreCaseAndStatus(
                                    snapshot.variantId(), normalizedSerial, WarrantyStatus.ACTIVE);
                    if (retry.isPresent()
                            && Objects.equals(retry.get().getOrderItemId(), command.orderItemId())
                            && Objects.equals(retry.get().getDeliveryId(), command.deliveryId())) {
                        return buildDetailResponse(retry.get());
                    }
                } else {
                    Optional<WarrantyRecord> retry = warrantyRecordRepository
                            .findFirstByOrderItemIdAndDeliveryIdAndSerialNumberIsNullAndStatus(
                                    command.orderItemId(), command.deliveryId(), WarrantyStatus.ACTIVE);
                    if (retry.isPresent()) {
                        return buildDetailResponse(retry.get());
                    }
                }
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public WarrantyRecordDetailResponse voidRecord(UUID warrantyRecordId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_VOID_REASON_REQUIRED);
        }
        WarrantyRecord record = warrantyRecordRepository.findById(warrantyRecordId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_NOT_FOUND, warrantyRecordId));

        if (record.getStatus() == WarrantyStatus.VOIDED) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_ALREADY_VOIDED, record.getWarrantyCode());
        }

        boolean hasOpenTicket = warrantyTicketRepository
                .findFirstByWarrantyRecordIdAndStatusIn(warrantyRecordId, OPEN_TICKET_STATUSES).isPresent();
        if (hasOpenTicket) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_HAS_OPEN_TICKET);
        }

        UUID actorId = iamApi.getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now(servicedeskClock);
        Map<String, Object> oldSnapshot = auditSnapshot(record);

        record.voidRecord(reason.trim(), actorId, now);
        WarrantyRecord saved = warrantyRecordRepository.save(record);

        eventPublisher.publishEvent(new WarrantyRecordAuditEvent(
                UUID.randomUUID(),
                saved.getWarrantyRecordId(),
                actorId,
                "VOID_WARRANTY",
                oldSnapshot,
                auditSnapshot(saved),
                now
        ));

        return buildDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WarrantyRecordListItemResponse> getMyRecords(WarrantyRecordFilterRequest filter) {
        UUID currentUserId = iamApi.getCurrentUserId();
        CustomerSummaryDto customer = crmApi.getCustomerByUserId(currentUserId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.CUSTOMER_NOT_FOUND, currentUserId));

        WarrantyRecordFilterRequest scopedFilter = filter != null ? filter : new WarrantyRecordFilterRequest();
        scopedFilter.setCustomerId(customer.customerId());
        scopedFilter.setPhone(null);
        return searchRecords(scopedFilter);
    }

    @Override
    @Transactional(readOnly = true)
    public WarrantyRecordDetailResponse getMyRecordDetail(UUID warrantyRecordId) {
        UUID currentUserId = iamApi.getCurrentUserId();
        CustomerSummaryDto customer = crmApi.getCustomerByUserId(currentUserId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.CUSTOMER_NOT_FOUND, currentUserId));

        WarrantyRecord record = warrantyRecordRepository.findById(warrantyRecordId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_NOT_FOUND, warrantyRecordId));

        if (!Objects.equals(record.getCustomerId(), customer.customerId())) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_FORBIDDEN);
        }
        return buildDetailResponse(record);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarrantyPolicySummaryResponse> getActivePolicies() {
        return catalogWarrantyApi.getActiveWarrantyPolicies().stream()
                .map(p -> WarrantyPolicySummaryResponse.builder()
                        .warrantyPolicyId(p.warrantyPolicyId())
                        .policyCode(p.policyCode())
                        .policyName(p.policyName())
                        .warrantyMonths(p.warrantyMonths())
                        .warrantyTerms(p.warrantyTerms())
                        .status(p.policyStatus())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogWarrantyPolicyDto getVariantWarrantyInfo(UUID variantId) {
        return catalogWarrantyApi.getWarrantyInfoByVariantId(variantId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_VARIANT_NOT_FOUND, variantId));
    }

    private CatalogWarrantyPolicyDto resolveAdHocPolicy(CatalogWarrantyPolicyDto variantInfo, UUID overridePolicyId) {
        if (overridePolicyId != null) {
            return catalogWarrantyApi.getWarrantyPolicyById(overridePolicyId)
                    .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_POLICY_NOT_APPLICABLE));
        }
        return variantInfo;
    }

    private void assertPolicyEligible(CatalogWarrantyPolicyDto policy) {
        if (policy == null
                || policy.warrantyPolicyId() == null
                || policy.warrantyMonths() == null
                || policy.warrantyMonths() <= 0
                || !"ACTIVE".equalsIgnoreCase(policy.policyStatus())) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_POLICY_NOT_APPLICABLE);
        }
    }

    private void validateSerialRule(CatalogWarrantyPolicyDto variantInfo, String normalizedSerial) {
        if (Boolean.TRUE.equals(variantInfo.serialTracking()) && normalizedSerial == null) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_SERIAL_REQUIRED);
        }
        if (normalizedSerial != null) {
            boolean duplicated = warrantyRecordRepository.existsActiveByVariantIdAndSerialNumber(
                    variantInfo.variantId(), normalizedSerial, WarrantyStatus.ACTIVE);
            if (duplicated) {
                throw new AppException(ServicedeskErrorCode.WARRANTY_DUPLICATE_ACTIVE_SERIAL, normalizedSerial);
            }
        }
    }

    private WarrantyRecord persistNewRecord(
            UUID customerId,
            UUID orderId,
            UUID orderItemId,
            UUID deliveryId,
            UUID variantId,
            CatalogWarrantyPolicyDto policy,
            String normalizedSerial,
            LocalDate startDate,
            UUID actorId,
            String auditAction
    ) {
        OffsetDateTime now = OffsetDateTime.now(servicedeskClock);
        LocalDate endDate = startDate.plusMonths(policy.warrantyMonths());
        String warrantyCode = warrantyCodeGenerator.nextCode(startDate);

        WarrantyRecord record = WarrantyRecord.builder()
                .warrantyCode(warrantyCode)
                .warrantyPolicyId(policy.warrantyPolicyId())
                .customerId(customerId)
                .orderId(orderId)
                .orderItemId(orderItemId)
                .deliveryId(deliveryId)
                .variantId(variantId)
                .serialNumber(normalizedSerial)
                .startDate(startDate)
                .endDate(endDate)
                .warrantyMonths(policy.warrantyMonths())
                .warrantyTerms(policy.warrantyTerms())
                .status(WarrantyStatus.ACTIVE)
                .createdBy(actorId)
                .createdAt(now)
                .updatedAt(now)
                .build();

        WarrantyRecord saved;
        try {
            saved = warrantyRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(
                    ServicedeskErrorCode.WARRANTY_DUPLICATE_ACTIVE_SERIAL,
                    normalizedSerial != null ? normalizedSerial : "N/A");
        }

        if (actorId != null) {
            eventPublisher.publishEvent(new WarrantyRecordAuditEvent(
                    UUID.randomUUID(),
                    saved.getWarrantyRecordId(),
                    actorId,
                    auditAction,
                    null,
                    auditSnapshot(saved),
                    now
            ));
        }
        return saved;
    }

    private PageResponse<WarrantyRecordListItemResponse> toPageResponse(Page<WarrantyRecord> page, LocalDate today) {
        List<WarrantyRecord> records = page.getContent();
        if (records.isEmpty()) {
            return PageResponse.of(List.of(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }

        List<UUID> recordIds = records.stream().map(WarrantyRecord::getWarrantyRecordId).toList();
        Set<UUID> variantIds = records.stream().map(WarrantyRecord::getVariantId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<UUID> customerIds = records.stream().map(WarrantyRecord::getCustomerId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<UUID> orderIds = records.stream().map(WarrantyRecord::getOrderId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());

        Map<UUID, CatalogSkuSummaryDto> skuMap = catalogApi.getSkuSummaries(variantIds);
        Map<UUID, CustomerSummaryDto> customerMap = crmApi.getCustomersByIds(customerIds);
        Map<UUID, String> orderCodeMap = salesQueryService.getOrderCodesByIds(orderIds);
        Map<UUID, String> openTicketsMap = recordIds.isEmpty() ? Map.of() : warrantyTicketRepository
                .findTicketsByWarrantyRecordIdsAndStatuses(recordIds, OPEN_TICKET_STATUSES)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        WarrantyTicket::getWarrantyRecordId,
                        WarrantyTicket::getTicketCode,
                        (existing, replacing) -> existing
                ));

        List<WarrantyRecordListItemResponse> items = records.stream().map(r -> {
            CatalogSkuSummaryDto sku = skuMap.get(r.getVariantId());
            CustomerSummaryDto customer = customerMap.get(r.getCustomerId());
            String openTicketCode = openTicketsMap.get(r.getWarrantyRecordId());
            boolean hasOpenTicket = openTicketCode != null;
            boolean canRequestSupport = r.getStatus() == WarrantyStatus.ACTIVE && !hasOpenTicket;
            return WarrantyRecordListItemResponse.builder()
                    .warrantyRecordId(r.getWarrantyRecordId())
                    .warrantyCode(r.getWarrantyCode())
                    .variantId(r.getVariantId())
                    .sku(sku != null ? sku.sku() : null)
                    .productName(sku != null ? sku.productName() : null)
                    .variantName(sku != null ? sku.variantName() : null)
                    .serialNumber(r.getSerialNumber())
                    .customerId(r.getCustomerId())
                    .customerName(customer != null ? customer.customerName() : null)
                    .phoneNumber(customer != null ? customer.phone() : null)
                    .orderId(r.getOrderId())
                    .orderCode(r.getOrderId() != null ? orderCodeMap.get(r.getOrderId()) : null)
                    .startDate(r.getStartDate())
                    .endDate(r.getEndDate())
                    .remainingDays(r.computeRemainingDays(today))
                    .warrantyMonths(r.getWarrantyMonths())
                    .status(r.getStatus())
                    .displayStatus(r.computeDisplayStatus(today, hasOpenTicket))
                    .canRequestSupport(canRequestSupport)
                    .openTicketCode(openTicketCode)
                    .createdAt(r.getCreatedAt())
                    .build();
        }).toList();

        return PageResponse.of(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private WarrantyRecordDetailResponse buildDetailResponse(WarrantyRecord record) {
        LocalDate today = LocalDate.now(servicedeskClock);

        CatalogSkuSummaryDto sku = catalogApi.getSkuSummary(record.getVariantId()).orElse(null);
        CatalogWarrantyPolicyDto policy = record.getWarrantyPolicyId() != null
                ? catalogWarrantyApi.getWarrantyPolicyById(record.getWarrantyPolicyId()).orElse(null)
                : null;
        CustomerSummaryDto customer = crmApi.getCustomerById(record.getCustomerId()).orElse(null);
        List<CustomerAddressDto> addresses = crmApi.getDeliveryAddressesByCustomerId(record.getCustomerId());
        SalesOrderWarrantySummaryDto orderSummary = record.getOrderId() != null
                ? salesQueryService.getOrderSummaryWithInvoice(record.getOrderId()).orElse(null)
                : null;

        String installationAddress = orderSummary != null && orderSummary.deliveryAddress() != null && !orderSummary.deliveryAddress().isBlank()
                ? orderSummary.deliveryAddress()
                : addresses.stream()
                        .findFirst()
                        .map(CustomerAddressDto::addressText)
                        .orElse(null);

        FulfillmentWarrantyPort.DeliveryNoteSnapshot deliverySnapshot = record.getDeliveryId() != null
                ? fulfillmentWarrantyPort.findDeliverySummary(record.getDeliveryId()).orElse(null)
                : null;

        Optional<WarrantyTicket> openTicketOpt = warrantyTicketRepository.findFirstByWarrantyRecordIdAndStatusIn(
                record.getWarrantyRecordId(), List.of(WarrantyTicketStatus.NEW, WarrantyTicketStatus.IN_PROGRESS));
        String openTicketCode = openTicketOpt.map(WarrantyTicket::getTicketCode).orElse(null);
        boolean hasOpenTicket = openTicketCode != null;
        boolean canRequestSupport = record.getStatus() == WarrantyStatus.ACTIVE && !hasOpenTicket;

        WarrantyRecordDetailResponse.OrderInvoiceInfo orderInvoiceInfo = orderSummary == null ? null :
                WarrantyRecordDetailResponse.OrderInvoiceInfo.builder()
                        .orderId(orderSummary.orderId())
                        .orderItemId(record.getOrderItemId())
                        .orderCode(orderSummary.orderCode())
                        .orderStatus(orderSummary.orderStatus())
                        .paymentStatus(orderSummary.paymentStatus())
                        .orderDate(orderSummary.orderDate())
                        .deliveryAddress(orderSummary.deliveryAddress())
                        .invoiceId(orderSummary.invoiceId())
                        .invoiceCode(orderSummary.invoiceCode())
                        .invoiceType(orderSummary.invoiceType())
                        .invoiceStatus(orderSummary.invoiceStatus())
                        .issueDate(orderSummary.issueDate())
                        .invoiceTotalAmount(orderSummary.invoiceTotalAmount())
                        .build();

        WarrantyRecordDetailResponse.DeliveryNoteInfo deliveryNoteInfo = deliverySnapshot == null ? null :
                WarrantyRecordDetailResponse.DeliveryNoteInfo.builder()
                        .deliveryId(deliverySnapshot.deliveryId())
                        .deliveryCode(deliverySnapshot.deliveryCode())
                        .status(deliverySnapshot.status())
                        .deliveredAt(deliverySnapshot.deliveredAt())
                        .note(deliverySnapshot.note())
                        .build();

        return WarrantyRecordDetailResponse.builder()
                .warrantyRecordId(record.getWarrantyRecordId())
                .warrantyCode(record.getWarrantyCode())
                .status(record.getStatus())
                .displayStatus(record.computeDisplayStatus(today, hasOpenTicket))
                .canRequestSupport(canRequestSupport)
                .openTicketCode(openTicketCode)
                .startDate(record.getStartDate())
                .endDate(record.getEndDate())
                .warrantyMonths(record.getWarrantyMonths())
                .remainingDays(record.computeRemainingDays(today))
                .voidReason(record.getVoidReason())
                .voidedAt(record.getVoidedAt())
                .voidedBy(record.getVoidedBy())
                .createdBy(record.getCreatedBy())
                .createdAt(record.getCreatedAt())
                .variantId(record.getVariantId())
                .sku(sku != null ? sku.sku() : null)
                .productName(sku != null ? sku.productName() : null)
                .variantName(sku != null ? sku.variantName() : null)
                .serialTracking(sku != null ? sku.serialTracking() : null)
                .serialNumber(record.getSerialNumber())
                .warrantyPolicyId(record.getWarrantyPolicyId())
                .policyCode(policy != null ? policy.policyCode() : null)
                .policyName(policy != null ? policy.policyName() : null)
                .warrantyTerms(record.getWarrantyTerms())
                .customerId(record.getCustomerId())
                .customerCode(customer != null ? customer.customerCode() : null)
                .customerName(customer != null ? customer.customerName() : null)
                .phoneNumber(customer != null ? customer.phone() : null)
                .customerEmail(customer != null ? customer.email() : null)
                .installationAddress(installationAddress)
                .orderInvoice(orderInvoiceInfo)
                .deliveryNote(deliveryNoteInfo)
                .build();
    }

    private Specification<WarrantyRecord> buildSpec(
            WarrantyRecordFilterRequest filter,
            Collection<UUID> phoneCustomerIds,
            LocalDate today
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String pattern = "%" + filter.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("warrantyCode")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("serialNumber"), "")), pattern)
                ));
            }
            if (filter.getSerialNumber() != null && !filter.getSerialNumber().isBlank()) {
                String pattern = "%" + filter.getSerialNumber().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("serialNumber"), "")), pattern));
            }
            if (phoneCustomerIds != null && !phoneCustomerIds.isEmpty()) {
                predicates.add(root.get("customerId").in(phoneCustomerIds));
            }
            if (filter.getCustomerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), filter.getCustomerId()));
            }
            if (filter.getOrderId() != null) {
                predicates.add(cb.equal(root.get("orderId"), filter.getOrderId()));
            }
            if (filter.getVariantId() != null) {
                predicates.add(cb.equal(root.get("variantId"), filter.getVariantId()));
            }

            if (filter.getDisplayStatus() != null) {
                switch (filter.getDisplayStatus()) {
                    case VOIDED -> predicates.add(cb.equal(root.get("status"), WarrantyStatus.VOIDED));
                    case CLAIMED -> {
                        predicates.add(cb.equal(root.get("status"), WarrantyStatus.ACTIVE));
                        predicates.add(cb.exists(openTicketSubquery(root, query, cb)));
                    }
                    case EXPIRED -> {
                        predicates.add(cb.equal(root.get("status"), WarrantyStatus.ACTIVE));
                        predicates.add(cb.lessThan(root.get("endDate"), today));
                        predicates.add(cb.not(cb.exists(openTicketSubquery(root, query, cb))));
                    }
                    case ACTIVE -> {
                        predicates.add(cb.equal(root.get("status"), WarrantyStatus.ACTIVE));
                        predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), today));
                        predicates.add(cb.not(cb.exists(openTicketSubquery(root, query, cb))));
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Subquery<UUID> openTicketSubquery(
            Root<WarrantyRecord> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb
    ) {
        Subquery<UUID> sq = query.subquery(UUID.class);
        Root<WarrantyTicket> ticketRoot = sq.from(WarrantyTicket.class);
        sq.select(ticketRoot.get("ticketId"));
        sq.where(
                cb.equal(ticketRoot.get("warrantyRecordId"), root.get("warrantyRecordId")),
                ticketRoot.get("status").in(OPEN_TICKET_STATUSES)
        );
        return sq;
    }

    private String normalizeSerial(String serial) {
        if (serial == null || serial.isBlank()) {
            return null;
        }
        return serial.trim();
    }

    private Map<String, Object> auditSnapshot(WarrantyRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("warrantyCode", record.getWarrantyCode());
        map.put("status", record.getStatus().name());
        map.put("customerId", record.getCustomerId());
        map.put("variantId", record.getVariantId());
        map.put("orderId", record.getOrderId());
        map.put("orderItemId", record.getOrderItemId());
        map.put("serialNumber", record.getSerialNumber());
        map.put("startDate", record.getStartDate() != null ? record.getStartDate().toString() : null);
        map.put("endDate", record.getEndDate() != null ? record.getEndDate().toString() : null);
        map.put("warrantyMonths", record.getWarrantyMonths());
        map.put("voidReason", record.getVoidReason());
        return map;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEligible(UUID warrantyRecordId) {
        return getEligibility(warrantyRecordId).eligible();
    }

    @Override
    @Transactional(readOnly = true)
    public com.aquasuite.ims.dto.servicedesk.response.WarrantyEligibilityDto getEligibility(UUID warrantyRecordId) {
        WarrantyRecord record = warrantyRecordRepository.findById(warrantyRecordId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_NOT_FOUND, warrantyRecordId));

        LocalDate today = LocalDate.now(servicedeskClock);
        boolean hasOpenTicket = warrantyTicketRepository
                .findFirstByWarrantyRecordIdAndStatusIn(warrantyRecordId, OPEN_TICKET_STATUSES).isPresent();
        WarrantyDisplayStatus displayStatus = record.computeDisplayStatus(today, hasOpenTicket);

        boolean eligible = record.getStatus() == WarrantyStatus.ACTIVE
                && record.getEndDate() != null
                && !today.isAfter(record.getEndDate());

        return com.aquasuite.ims.dto.servicedesk.response.WarrantyEligibilityDto.builder()
                .warrantyRecordId(record.getWarrantyRecordId())
                .warrantyCode(record.getWarrantyCode())
                .eligible(eligible)
                .displayStatus(displayStatus.name())
                .startDate(record.getStartDate())
                .endDate(record.getEndDate())
                .remainingDays(record.computeRemainingDays(today))
                .voidReason(record.getVoidReason())
                .build();
    }

    // ── Customer Warranty Lookup (from WarrantyLookupServiceImpl) ────────────

    @Override
    @Transactional(readOnly = true)
    public List<WarrantyLookupResponse> findMine(String keyword) {
        UUID userId = iamApi.getCurrentUserId();
        Optional<UUID> customerId = crmApi.getCustomerByUserId(userId).map(customer -> customer.customerId());
        if (customerId.isEmpty()) return List.of();
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        LocalDate today = LocalDate.now(servicedeskClock != null ? servicedeskClock : Clock.systemUTC());
        return warrantyRecordRepository.findByCustomerIdOrderByStartDateDesc(customerId.get()).stream()
                .map(record -> toLookupResponse(record, today))
                .filter(response -> matchesLookup(response, normalizedKeyword))
                .toList();
    }

    private WarrantyLookupResponse toLookupResponse(WarrantyRecord record, LocalDate today) {
        com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto sku = catalogApi != null && record.getVariantId() != null
                ? catalogApi.getSkuSummary(record.getVariantId()).orElse(null)
                : null;
        SalesOrderItemSummaryDto orderItem = (record.getOrderItemId() == null || salesQueryService == null)
                ? null
                : salesQueryService.getOrderItemSummary(record.getOrderItemId()).orElse(null);
        String productName = firstText(orderItem == null ? null : orderItem.productName(), sku == null ? null : sku.productName(), "Sản phẩm");
        String variantName = firstText(orderItem == null ? null : orderItem.variantName(), sku == null ? null : sku.variantName(), null);
        String skuCode = firstText(orderItem == null ? null : orderItem.sku(), sku == null ? null : sku.sku(), null);
        boolean voided = record.getStatus() == WarrantyStatus.VOIDED;
        boolean active = !voided && record.getEndDate() != null && !today.isAfter(record.getEndDate());
        long remainingDays = active ? Math.max(0, ChronoUnit.DAYS.between(today, record.getEndDate())) : 0;
        String status = voided ? "VOIDED" : (active ? "ACTIVE" : "EXPIRED");
        return WarrantyLookupResponse.builder().warrantyRecordId(record.getWarrantyRecordId())
                .productName(productName).variantName(variantName).sku(skuCode).serialNumber(record.getSerialNumber())
                .orderCode(orderItem == null ? null : orderItem.orderCode()).warrantyTerms(record.getWarrantyTerms())
                .startDate(record.getStartDate()).endDate(record.getEndDate()).remainingDays(remainingDays)
                .status(status).claimAllowed(active).build();
    }

    private boolean matchesLookup(WarrantyLookupResponse record, String keyword) {
        return keyword == null || containsLookup(record.serialNumber(), keyword) || containsLookup(record.orderCode(), keyword);
    }

    private boolean containsLookup(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String firstText(String first, String fallback, String defaultValue) {
        if (StringUtils.hasText(first)) return first;
        if (StringUtils.hasText(fallback)) return fallback;
        return defaultValue;
    }
}
