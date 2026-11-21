package com.aquasuite.ims.service.servicedesk.impl;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.event.servicedesk.WarrantyTicketAuditEvent;
import com.aquasuite.ims.entity.servicedesk.WarrantyRecord;
import com.aquasuite.ims.entity.servicedesk.WarrantyTicket;
import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import com.aquasuite.ims.exception.servicedesk.ServicedeskErrorCode;
import com.aquasuite.ims.dto.servicedesk.request.DispatchWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.RejectWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyTicketFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.TechnicalJobDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketListItemResponse;
import com.aquasuite.ims.repository.servicedesk.WarrantyRecordRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyTicketRepository;
import com.aquasuite.ims.service.servicedesk.TechnicalJobService;
import com.aquasuite.ims.service.servicedesk.WarrantyTicketManagementService;
import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.exception.AppException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarrantyTicketManagementServiceImpl implements WarrantyTicketManagementService {

    private final WarrantyTicketRepository warrantyTicketRepository;
    private final WarrantyRecordRepository warrantyRecordRepository;
    private final CatalogLookupService catalogApi;
    private final TechnicalJobService technicalJobService;
    private final IamQueryService iamApi;
    private final ApplicationEventPublisher eventPublisher;

    @Qualifier("servicedeskClock")
    private final Clock servicedeskClock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WarrantyTicketListItemResponse> list(WarrantyTicketFilterRequest filter) {
        int page = filter != null && filter.getPage() != null && filter.getPage() >= 0 ? filter.getPage() : 0;
        int size = filter != null && filter.getSize() != null && filter.getSize() > 0 ? filter.getSize() : 20;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<WarrantyTicket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null && filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<WarrantyTicket> ticketPage = warrantyTicketRepository.findAll(spec, pageable);
        List<WarrantyTicket> tickets = ticketPage.getContent();

        Set<UUID> recordIds = tickets.stream().map(WarrantyTicket::getWarrantyRecordId).collect(Collectors.toSet());
        Map<UUID, WarrantyRecord> recordMap = warrantyRecordRepository.findAllById(recordIds).stream()
                .collect(Collectors.toMap(WarrantyRecord::getWarrantyRecordId, r -> r));

        Set<UUID> variantIds = recordMap.values().stream()
                .map(WarrantyRecord::getVariantId).filter(Objects::nonNull).collect(Collectors.toSet());
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
                    .canCancel(false)
                    .mediaCount(t.getMedia() != null ? t.getMedia().size() : 0)
                    .createdAt(t.getCreatedAt())
                    .build();
        }).toList();

        return PageResponse.of(items, ticketPage.getNumber(), ticketPage.getSize(),
                ticketPage.getTotalElements(), ticketPage.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public WarrantyTicketDetailResponse getById(UUID ticketId) {
        WarrantyTicket ticket = warrantyTicketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_TICKET_NOT_FOUND, ticketId));
        WarrantyRecord record = warrantyRecordRepository.findById(ticket.getWarrantyRecordId()).orElse(null);
        return toDetailResponse(ticket, record);
    }

    @Override
    @Transactional
    public WarrantyTicketDetailResponse dispatch(UUID ticketId, DispatchWarrantyTicketRequest request) {
        WarrantyTicket ticket = warrantyTicketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_TICKET_NOT_FOUND, ticketId));

        // Chỉ bắn job khi ticket đang chờ tiếp nhận (NEW) — chặn sớm trước khi tạo job để tránh job mồ côi.
        if (ticket.getStatus() != WarrantyTicketStatus.NEW) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_TICKET_NOT_DISPATCHABLE, ticket.getStatus().name());
        }

        String serviceAddress = StringUtils.hasText(request.serviceAddress())
                ? request.serviceAddress().trim()
                : ticket.getSiteAddress();

        TechnicalJobDetailResponse job = technicalJobService.dispatchWarrantyRepairJob(
                ticket.getCustomerId(),
                ticket.getWarrantyRecordId(),
                ticket.getTicketId(),
                request.technicianId(),
                serviceAddress,
                ticket.getIssueDescription(),
                request.scheduledAt());

        ticket.markInProgress(job.technicalJobId(), OffsetDateTime.now());
        WarrantyTicket saved = warrantyTicketRepository.save(ticket);

        WarrantyRecord record = warrantyRecordRepository.findById(saved.getWarrantyRecordId()).orElse(null);
        log.info("Đã bắn công việc sửa chữa bảo hành {} cho ticket {} (KTV {})",
                job.technicalJobId(), saved.getTicketCode(), request.technicianId());
        return toDetailResponse(saved, record);
    }

    @Override
    @Transactional
    public WarrantyTicketDetailResponse reject(UUID ticketId, RejectWarrantyTicketRequest request) {
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_TICKET_REJECT_REASON_REQUIRED);
        }
        WarrantyTicket ticket = warrantyTicketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_TICKET_NOT_FOUND, ticketId));

        OffsetDateTime now = OffsetDateTime.now();
        ticket.reject(request.reason(), now);
        WarrantyTicket saved = warrantyTicketRepository.save(ticket);

        eventPublisher.publishEvent(new WarrantyTicketAuditEvent(
                UUID.randomUUID(),
                saved.getTicketId(),
                iamApi.getCurrentUserId(),
                "REJECTED",
                Map.of("status", "NEW"),
                Map.of(
                        "status", "REJECTED",
                        "rejectionReason", saved.getRejectionReason() != null ? saved.getRejectionReason() : ""
                ),
                now
        ));

        WarrantyRecord record = warrantyRecordRepository.findById(saved.getWarrantyRecordId()).orElse(null);
        log.info("Đã từ chối yêu cầu bảo hành {} tại bàn", saved.getTicketCode());
        return toDetailResponse(saved, record);
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
                .canCancel(false)
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
