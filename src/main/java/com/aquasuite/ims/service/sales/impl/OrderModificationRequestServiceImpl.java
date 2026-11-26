package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.service.finance.PaymentService;
import com.aquasuite.ims.service.finance.PaymentAllocationService;
import com.aquasuite.ims.dto.finance.command.OrderModificationSettlementCommand;
import com.aquasuite.ims.dto.finance.command.PendingCustomerRefundCommand;
import com.aquasuite.ims.service.fulfillment.DeliveryCodQueryService;
import com.aquasuite.ims.service.inventory.StockReservationService;
import com.aquasuite.ims.event.sales.OrderModificationRequestReviewedEvent;
import com.aquasuite.ims.event.sales.OrderModificationResultEmailRequestedEvent;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.entity.sales.OrderModificationRequest;
import com.aquasuite.ims.entity.sales.OrderModificationRequestItem;
import com.aquasuite.ims.entity.sales.SalesInvoice;
import com.aquasuite.ims.entity.sales.SalesInvoiceItem;
import com.aquasuite.ims.enums.sales.OrderModificationItemAction;
import com.aquasuite.ims.enums.sales.OrderModificationRequestStatus;
import com.aquasuite.ims.enums.sales.OrderModificationRequestType;
import com.aquasuite.ims.enums.sales.OrderStatus;
import com.aquasuite.ims.enums.sales.SalesInvoiceStatus;
import com.aquasuite.ims.exception.sales.OrderModificationErrorCode;
import com.aquasuite.ims.mapper.sales.OrderModificationRequestMapper;
import com.aquasuite.ims.dto.sales.request.ApproveOrderModificationRequest;
import com.aquasuite.ims.dto.sales.request.CreateOrderModificationRequest;
import com.aquasuite.ims.dto.sales.request.RecordOrderModificationTaxResultRequest;
import com.aquasuite.ims.dto.sales.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.dto.sales.response.OrderModificationRequestResponse;
import com.aquasuite.ims.repository.sales.OrderItemRepository;
import com.aquasuite.ims.repository.sales.OrderModificationRequestItemRepository;
import com.aquasuite.ims.repository.sales.OrderModificationRequestRepository;
import com.aquasuite.ims.repository.sales.OrderRepository;
import com.aquasuite.ims.repository.sales.SalesInvoiceItemRepository;
import com.aquasuite.ims.repository.sales.SalesInvoiceRepository;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** UC-26 review lifecycle. Direct order, reservation and Draft-invoice changes share one transaction. */
@Service
@RequiredArgsConstructor
public class OrderModificationRequestServiceImpl {
    private final OrderModificationRequestRepository requestRepository;
    private final OrderModificationRequestItemRepository requestItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final SalesInvoiceItemRepository invoiceItemRepository;
    private final OrderModificationRequestMapper mapper;
    private final DeliveryCodQueryService deliveries;
    private final PaymentService payments;
    private final PaymentAllocationService paymentAllocations;
    private final CatalogLookupService catalogApi;
    private final StockReservationService reservations;
    private final ApplicationEventPublisher events;
    @Transactional(readOnly = true)
    public Page<OrderModificationRequestResponse> list(Pageable page, boolean accountantOnly) {
        Pageable safePage = com.aquasuite.ims.util.PageableUtils.sanitizePageable(
                page, java.util.Set.of("createdAt", "updatedAt", "requestStatus", "requestType"));
        Page<OrderModificationRequest> requests = accountantOnly
                ? requestRepository.findByRequestStatusOrderByCreatedAtDesc(OrderModificationRequestStatus.PENDING_CANCEL, safePage)
                : requestRepository.findAllByOrderByCreatedAtDesc(safePage);
        return requests.map(this::toResponse);
    }
    @Transactional(readOnly = true)
    public OrderModificationRequestResponse detail(UUID id, boolean accountantOnly) {
        OrderModificationRequest request = find(id);
        if (accountantOnly && request.getRequestStatus() != OrderModificationRequestStatus.PENDING_CANCEL) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
        return toResponse(request);
    }
    @Transactional
    public OrderModificationRequestResponse create(CreateOrderModificationRequest body, UUID actorId) {
        OrderModificationRequestType requestType = parseRequestType(body.getRequestType());

        Order order = orderRepository.findByIdForUpdate(body.getOrderId())
                .orElseThrow(() -> new AppException(OrderModificationErrorCode.ORDER_NOT_ELIGIBLE));

        // Check order eligibility: PROCESSING and no dispatched deliveries
        boolean dispatched = deliveries.lockByOrderId(order.getOrderId()).stream()
                .map(delivery -> delivery.status() == null ? "" : delivery.status().toUpperCase())
                .anyMatch(status -> List.of("DISPATCHED", "IN_TRANSIT", "DELIVERED").contains(status));
        if (!OrderStatus.PROCESSING.name().equalsIgnoreCase(order.getOrderStatus()) || dispatched) {
            throw new AppException(OrderModificationErrorCode.ORDER_NOT_ELIGIBLE);
        }

        // Block duplicate active requests
        if (requestRepository.existsByOrderIdAndRequestStatusIn(order.getOrderId(),
                List.of(OrderModificationRequestStatus.PENDING_REVIEW, OrderModificationRequestStatus.PENDING_CANCEL))) {
            throw new AppException(OrderModificationErrorCode.DUPLICATE_ACTIVE_REQUEST);
        }

        // Validate per request type
        if (requestType == OrderModificationRequestType.ADDRESS_CHANGE) {
            if (body.getRequestedDeliveryAddress() == null || body.getRequestedDeliveryAddress().isBlank()) {
                throw new AppException(OrderModificationErrorCode.ADDRESS_REQUIRED);
            }
        }

        BigDecimal priceDifference = BigDecimal.ZERO;
        List<OrderModificationRequestItem> savedItems = new ArrayList<>();

        if (requestType == OrderModificationRequestType.ITEM_CHANGE) {
            if (body.getItems() == null || body.getItems().isEmpty()) {
                throw new AppException(OrderModificationErrorCode.ITEMS_REQUIRED_FOR_ITEM_CHANGE);
            }

            Map<UUID, OrderItem> currentItems = orderItemRepository.findByOrderIdForUpdate(order.getOrderId()).stream()
                    .collect(Collectors.toMap(OrderItem::getOrderItemId, item -> item));

            // Build request first to get its ID
            OrderModificationRequest request = OrderModificationRequest.builder()
                    .modificationRequestId(UUID.randomUUID())
                    .orderId(order.getOrderId())
                    .customerId(order.getCustomerId())
                    .requestType(requestType)
                    .requestStatus(OrderModificationRequestStatus.PENDING_REVIEW)
                    .customerReason(body.getCustomerReason().trim())
                    .requestedDeliveryAddress(trimToNull(body.getRequestedDeliveryAddress()))
                    .requestedBy(actorId)
                    .priceDifference(BigDecimal.ZERO)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
            request = requestRepository.save(request);

            BigDecimal delta = BigDecimal.ZERO;
            HashSet<UUID> seen = new HashSet<>();
            for (CreateOrderModificationRequest.CreateOrderModificationItemRequest itemReq : body.getItems()) {
                OrderItem current = currentItems.get(itemReq.getOrderItemId());
                OrderModificationItemAction action = parseItemAction(itemReq.getChangeAction());

                if (!seen.add(itemReq.getOrderItemId()) || current == null
                        || !order.getOrderId().equals(current.getOrderId())) {
                    throw new AppException(OrderModificationErrorCode.INVALID_REQUESTED_ITEM);
                }

                // Validate action-specific constraints
                if (action == OrderModificationItemAction.REDUCE) {
                    if (itemReq.getRequestedQuantity() == null || itemReq.getRequestedQuantity().signum() <= 0
                            || itemReq.getRequestedQuantity().compareTo(current.getQuantity()) >= 0) {
                        throw new AppException(OrderModificationErrorCode.INVALID_REQUESTED_ITEM);
                    }
                }
                if (action == OrderModificationItemAction.REMOVE) {
                    if (itemReq.getRequestedQuantity() != null && itemReq.getRequestedQuantity().signum() != 0) {
                        throw new AppException(OrderModificationErrorCode.INVALID_REQUESTED_ITEM);
                    }
                }
                if (action == OrderModificationItemAction.SWAP) {
                    if (itemReq.getRequestedVariantId() == null
                            || itemReq.getRequestedVariantId().equals(current.getVariantId())) {
                        throw new AppException(OrderModificationErrorCode.INVALID_REQUESTED_ITEM);
                    }
                    CatalogSkuSummaryDto sku = catalogApi.getSkuSummary(itemReq.getRequestedVariantId())
                            .filter(s -> s.status() == null || "ACTIVE".equalsIgnoreCase(s.status()))
                            .orElseThrow(() -> new AppException(OrderModificationErrorCode.REPLACEMENT_SKU_NOT_FOUND));
                    delta = delta.add(amount(sku.sellingPrice()).subtract(current.getUnitPrice())
                            .multiply(current.getQuantity()));
                }

                if (action == OrderModificationItemAction.REMOVE) {
                    delta = delta.subtract(current.getUnitPrice().multiply(current.getQuantity()));
                } else if (action == OrderModificationItemAction.REDUCE) {
                    delta = delta.subtract(current.getUnitPrice()
                            .multiply(current.getQuantity().subtract(itemReq.getRequestedQuantity())));
                }

                OrderModificationRequestItem item = OrderModificationRequestItem.builder()
                        .modificationRequestItemId(UUID.randomUUID())
                        .modificationRequestId(request.getModificationRequestId())
                        .orderItemId(current.getOrderItemId())
                        .originalVariantId(current.getVariantId())
                        .requestedVariantId(action == OrderModificationItemAction.SWAP ? itemReq.getRequestedVariantId() : null)
                        .changeAction(action)
                        .originalQuantity(current.getQuantity())
                        .requestedQuantity(itemReq.getRequestedQuantity())
                        .unitPriceSnapshot(current.getUnitPrice())
                        .createdAt(OffsetDateTime.now())
                        .build();
                savedItems.add(requestItemRepository.save(item));
            }

            priceDifference = delta;
            request.setPriceDifference(priceDifference);

            audit(actorId, request, "CREATED", request.getRequestStatus(), request.getRequestStatus(), request.getCustomerReason());
            return toResponseWithItems(request, savedItems);
        }

        // ADDRESS_CHANGE or FULL_CANCELLATION without items
        OrderModificationRequest request = OrderModificationRequest.builder()
                .modificationRequestId(UUID.randomUUID())
                .orderId(order.getOrderId())
                .customerId(order.getCustomerId())
                .requestType(requestType)
                .requestStatus(OrderModificationRequestStatus.PENDING_REVIEW)
                .customerReason(body.getCustomerReason().trim())
                .requestedDeliveryAddress(trimToNull(body.getRequestedDeliveryAddress()))
                .requestedBy(actorId)
                .priceDifference(priceDifference)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        request = requestRepository.save(request);

        audit(actorId, request, "CREATED", request.getRequestStatus(), request.getRequestStatus(), request.getCustomerReason());
        return toResponse(request);
    }
    @Transactional
    public OrderModificationRequestResponse reject(UUID id, ReviewOrderModificationRequest body, UUID actorId) {
        OrderModificationRequest request = lockReviewable(id);
        requireEligible(request);
        OrderModificationRequestStatus previous = request.getRequestStatus();
        request.setRequestStatus(OrderModificationRequestStatus.REJECTED);
        request.setDecisionNote(body.getDecisionNote().trim());
        request.setReviewedBy(actorId);
        request.setReviewedAt(OffsetDateTime.now());
        audit(actorId, request, "REJECTED", previous, request.getRequestStatus(), request.getDecisionNote());
        return toResponse(request);
    }
    @Transactional
    public OrderModificationRequestResponse approve(
            UUID id, ApproveOrderModificationRequest body, UUID actorId, boolean shopOwner
    ) {
        OrderModificationRequest request = lockReviewable(id);
        Order order = requireEligible(request);
        validateReviewerAuthority(request, shopOwner);
        validateAddress(request, body);

        request.setDecisionNote(body.getDecisionNote().trim());
        request.setRequestedDeliveryAddress(trimToNull(body.getRequestedDeliveryAddress()));
        request.setReplacementOrderId(body.getReplacementOrderId());
        request.setReviewedBy(actorId);
        request.setReviewedAt(OffsetDateTime.now());

        List<SalesInvoice> invoices = invoiceRepository.findByOrderIdForUpdate(order.getOrderId());
        BigDecimal verifiedAmount = amount(payments.getVerifiedPaymentSummary(order.getOrderId()).verifiedAmount());
        boolean taxLinked = invoices.stream().anyMatch(this::hasTaxInvoiceLink);
        boolean onlyDraftInvoices = invoices.stream()
                .filter(invoice -> !SalesInvoiceStatus.CANCELLED.name().equalsIgnoreCase(invoice.getStatus()))
                .allMatch(invoice -> SalesInvoiceStatus.DRAFT.name().equalsIgnoreCase(invoice.getStatus()));
        OrderModificationRequestStatus previous = request.getRequestStatus();

        // FIX: ADDRESS_CHANGE is handled first regardless of verifiedAmount or taxLinked
        if (request.getRequestType() == OrderModificationRequestType.ADDRESS_CHANGE) {
            order.setDeliveryAddress(request.getRequestedDeliveryAddress());
            request.setPriceDifference(BigDecimal.ZERO);
            request.setRequestStatus(OrderModificationRequestStatus.APPROVED);
        } else if (request.getRequestType() == OrderModificationRequestType.ITEM_CHANGE
                && !taxLinked && verifiedAmount.signum() == 0) {
            if (invoices.isEmpty() || !onlyDraftInvoices) {
                throw new AppException(OrderModificationErrorCode.DRAFT_INVOICE_REQUIRED);
            }
            applyStoreOnlyItemChange(order, request, actorId, invoices);
            request.setPriceDifference(amount(order.getTotalAmount()).subtract(verifiedAmount));
            request.setRequestStatus(OrderModificationRequestStatus.APPROVED);
        } else if (requiresPendingCancellation(taxLinked, verifiedAmount)) {
            request.setPriceDifference(proposedTotal(request, order.getTotalAmount()).subtract(verifiedAmount));
            request.setRequestStatus(OrderModificationRequestStatus.PENDING_CANCEL);
            invoiceRepository.findElectronicallyIntegratedInvoicesByOrderId(order.getOrderId()).stream()
                    .findFirst().ifPresent(invoice -> request.setOriginalInvoiceId(invoice.getInvoiceId()));
            createRefundWhenRequired(request, order, verifiedAmount, actorId);
        } else if (request.getRequestType() == OrderModificationRequestType.FULL_CANCELLATION) {
            if (!onlyDraftInvoices) {
                throw new AppException(OrderModificationErrorCode.DRAFT_INVOICE_REQUIRED);
            }
            request.setPriceDifference(verifiedAmount.negate());
            cancelOrder(order, request, actorId);
            request.setRequestStatus(OrderModificationRequestStatus.APPROVED);
        } else {
            order.setDeliveryAddress(request.getRequestedDeliveryAddress());
            request.setPriceDifference(amount(order.getTotalAmount()).subtract(verifiedAmount));
            request.setRequestStatus(OrderModificationRequestStatus.APPROVED);
        }

        audit(actorId, request, "APPROVED", previous, request.getRequestStatus(), request.getDecisionNote());
        return toResponse(request);
    }
    @Transactional
    public OrderModificationRequestResponse recordTaxResult(
            UUID id, RecordOrderModificationTaxResultRequest body, UUID actorId
    ) {
        OrderModificationRequest request = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(OrderModificationErrorCode.REQUEST_NOT_FOUND));
        if (request.getRequestStatus() != OrderModificationRequestStatus.PENDING_CANCEL) {
            throw new AppException(OrderModificationErrorCode.INVALID_REQUEST_STATE);
        }
        requireEligible(request);
        SalesInvoice resultInvoice = invoiceRepository.findByInvoiceIdForUpdate(body.getResultingInvoiceId())
                .orElseThrow(() -> new AppException(OrderModificationErrorCode.RESULT_INVOICE_NOT_FOUND));
        if (!request.getOrderId().equals(resultInvoice.getOrderId())
                && (body.getReplacementOrderId() == null || !body.getReplacementOrderId().equals(resultInvoice.getOrderId()))) {
            throw new AppException(OrderModificationErrorCode.RESULT_INVOICE_MISMATCH);
        }

        request.setTaxResultReference(body.getTaxResultReference().trim());
        request.setResultingInvoiceId(resultInvoice.getInvoiceId());
        request.setReplacementOrderId(body.getReplacementOrderId());
        if (refundIsStillPending(request)) {
            return toResponse(request);
        }

        reconcileVerifiedReceiptsForTaxResult(request, resultInvoice, actorId);

        OrderModificationRequestStatus previous = request.getRequestStatus();
        // FIX: Cancel original order when this is a FULL_CANCELLATION or has a replacement order
        if (request.getRequestType() == OrderModificationRequestType.FULL_CANCELLATION
                || request.getReplacementOrderId() != null) {
            Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                    .orElseThrow(() -> new AppException(OrderModificationErrorCode.ORDER_NOT_ELIGIBLE));
            cancelOrder(order, request, actorId);
        }
        request.setRequestStatus(OrderModificationRequestStatus.APPROVED);
        audit(actorId, request, "TAX_RESULT_RECORDED", previous, request.getRequestStatus(), request.getTaxResultReference());
        return toResponse(request);
    }

    private void reconcileVerifiedReceiptsForTaxResult(
            OrderModificationRequest request, SalesInvoice resultInvoice, UUID actorId
    ) {
        if (request.getOriginalInvoiceId() == null) return;
        boolean fullCancellation = request.getRequestType() == OrderModificationRequestType.FULL_CANCELLATION;
        // An Issued_Undelivered partial adjustment offsets its original invoice but keeps the same order.
        // Only a full cancellation or UC-21 replacement order may move receipt value across invoice/order boundaries.
        boolean replacementOrder = request.getReplacementOrderId() != null;
        if (!fullCancellation && !replacementOrder) return;
        paymentAllocations.reconcileOrderModification(new OrderModificationSettlementCommand(
                request.getOrderId(), request.getOriginalInvoiceId(),
                fullCancellation ? null : resultInvoice.getOrderId(),
                fullCancellation ? null : resultInvoice.getInvoiceId(),
                request.getRefundTransactionId(), actorId,
                "Đối soát phân bổ theo UC-26 " + request.getModificationRequestId()
        ));
    }
    @Transactional
    public void sendResultEmail(UUID requestId, UUID actorId) {
        OrderModificationRequest request = find(requestId);
        if (request.getRequestStatus() == OrderModificationRequestStatus.PENDING_REVIEW
                || request.getRequestStatus() == OrderModificationRequestStatus.PENDING_CANCEL) {
            throw new AppException(OrderModificationErrorCode.INVALID_REQUEST_STATE);
        }
        events.publishEvent(new OrderModificationResultEmailRequestedEvent(
                UUID.randomUUID(), request.getModificationRequestId(), request.getCustomerId(), Instant.now()));
        audit(actorId, request, "RESULT_EMAIL_REQUESTED", request.getRequestStatus(), request.getRequestStatus(),
                "Customer result email accepted for delivery");
    }

    private void validateReviewerAuthority(OrderModificationRequest request, boolean shopOwner) {
        if (!shopOwner && request.getRequestType() != OrderModificationRequestType.ADDRESS_CHANGE) {
            throw new AppException(OrderModificationErrorCode.OWNER_APPROVAL_REQUIRED);
        }
    }

    private void validateAddress(OrderModificationRequest request, ApproveOrderModificationRequest body) {
        if (request.getRequestType() == OrderModificationRequestType.ADDRESS_CHANGE
                && (body.getRequestedDeliveryAddress() == null || body.getRequestedDeliveryAddress().isBlank())) {
            throw new AppException(OrderModificationErrorCode.ADDRESS_REQUIRED);
        }
    }

    private boolean requiresPendingCancellation(boolean taxLinked, BigDecimal verifiedAmount) {
        return taxLinked || verifiedAmount.signum() > 0;
    }

    private void applyStoreOnlyItemChange(
            Order order, OrderModificationRequest request, UUID actorId, List<SalesInvoice> invoices
    ) {
        List<OrderModificationRequestItem> requestedItems = requestItemRepository
                .findByModificationRequestIdOrderByCreatedAtAsc(request.getModificationRequestId());
        if (requestedItems.isEmpty()) {
            throw new AppException(OrderModificationErrorCode.INVALID_ITEM_CHANGE);
        }
        Map<UUID, OrderItem> currentItems = orderItemRepository.findByOrderIdForUpdate(order.getOrderId()).stream()
                .collect(Collectors.toMap(OrderItem::getOrderItemId, item -> item));
        validateRequestedItems(requestedItems, currentItems, order.getOrderId());

        List<UUID> invoiceIds = invoices.stream()
                .filter(invoice -> !SalesInvoiceStatus.CANCELLED.name().equalsIgnoreCase(invoice.getStatus()))
                .map(SalesInvoice::getInvoiceId)
                .toList();
        if (invoiceIds.isEmpty()) {
            throw new AppException(OrderModificationErrorCode.DRAFT_INVOICE_REQUIRED);
        }
        List<SalesInvoiceItem> invoiceItems = invoiceItemRepository.findByInvoiceIdInForUpdate(invoiceIds);
        Map<UUID, List<SalesInvoiceItem>> byOrderItem = invoiceItems.stream()
                .collect(Collectors.groupingBy(SalesInvoiceItem::getOrderItemId));
        List<SalesInvoiceItem> toDelete = new ArrayList<>();
        for (OrderModificationRequestItem requested : requestedItems) {
            OrderItem current = currentItems.get(requested.getOrderItemId());
            List<SalesInvoiceItem> affectedInvoiceItems = byOrderItem.getOrDefault(current.getOrderItemId(), List.of());
            if (affectedInvoiceItems.isEmpty()) {
                throw new AppException(OrderModificationErrorCode.INVALID_ITEM_CHANGE);
            }
            switch (requested.getChangeAction()) {
                case REMOVE, REDUCE -> reduceOrRemoveLine(
                        current, requested, affectedInvoiceItems, toDelete, request, actorId);
                case SWAP -> swapLine(current, requested, affectedInvoiceItems, order, request, actorId);
            }
        }
        if (!toDelete.isEmpty()) {
            invoiceItemRepository.deleteAll(toDelete);
            invoiceItemRepository.flush();
        }
        recalculateDraftInvoicesAndOrder(order, invoices, invoiceIds);
    }

    private void validateRequestedItems(
            List<OrderModificationRequestItem> requestedItems, Map<UUID, OrderItem> currentItems, UUID orderId
    ) {
        HashSet<UUID> seen = new HashSet<>();
        for (OrderModificationRequestItem requested : requestedItems) {
            OrderItem current = currentItems.get(requested.getOrderItemId());
            if (!seen.add(requested.getOrderItemId()) || current == null || !orderId.equals(current.getOrderId())
                    || !requested.getOriginalVariantId().equals(current.getVariantId())
                    || requested.getOriginalQuantity().compareTo(current.getQuantity()) != 0) {
                throw new AppException(OrderModificationErrorCode.INVALID_ITEM_CHANGE);
            }
            if (requested.getChangeAction() == OrderModificationItemAction.REDUCE
                    && (requested.getRequestedQuantity() == null || requested.getRequestedQuantity().signum() <= 0
                    || requested.getRequestedQuantity().compareTo(current.getQuantity()) >= 0)) {
                throw new AppException(OrderModificationErrorCode.INVALID_ITEM_CHANGE);
            }
            if (requested.getChangeAction() == OrderModificationItemAction.REMOVE
                    && requested.getRequestedQuantity() != null && requested.getRequestedQuantity().signum() != 0) {
                throw new AppException(OrderModificationErrorCode.INVALID_ITEM_CHANGE);
            }
            if (requested.getChangeAction() == OrderModificationItemAction.SWAP
                    && (requested.getRequestedVariantId() == null || requested.getRequestedVariantId().equals(current.getVariantId())
                    || (requested.getRequestedQuantity() != null
                    && requested.getRequestedQuantity().compareTo(current.getQuantity()) != 0))) {
                throw new AppException(OrderModificationErrorCode.INVALID_ITEM_CHANGE);
            }
        }
    }

    private void reduceOrRemoveLine(
            OrderItem current, OrderModificationRequestItem requested, List<SalesInvoiceItem> affectedInvoiceItems,
            List<SalesInvoiceItem> toDelete, OrderModificationRequest request, UUID actorId
    ) {
        BigDecimal requestedQuantity = requested.getChangeAction() == OrderModificationItemAction.REMOVE
                ? BigDecimal.ZERO : requested.getRequestedQuantity();
        BigDecimal decrease = current.getQuantity().subtract(requestedQuantity);
        BigDecimal pendingDecrease = decrease;
        for (SalesInvoiceItem invoiceItem : affectedInvoiceItems.stream()
                .sorted(Comparator.comparing(SalesInvoiceItem::getInvoiceId).reversed()).toList()) {
            if (pendingDecrease.signum() == 0) {
                break;
            }
            BigDecimal moved = invoiceItem.getQuantity().min(pendingDecrease);
            BigDecimal remaining = invoiceItem.getQuantity().subtract(moved);
            if (remaining.signum() == 0) {
                toDelete.add(invoiceItem);
            } else {
                BigDecimal newDiscount = proportionalDiscount(invoiceItem.getDiscountAmount(), remaining, invoiceItem.getQuantity());
                invoiceItem.setQuantity(remaining);
                invoiceItem.setDiscountAmount(newDiscount);
                invoiceItem.setLineTotal(lineTotal(invoiceItem.getUnitPrice(), remaining, newDiscount));
            }
            pendingDecrease = pendingDecrease.subtract(moved);
        }
        if (pendingDecrease.signum() != 0) {
            throw new AppException(OrderModificationErrorCode.INVALID_ITEM_CHANGE);
        }
        releaseHeldReservations(current.getOrderItemId(), decrease, request, actorId);
        BigDecimal newDiscount = proportionalDiscount(current.getDiscountAmount(), requestedQuantity, current.getQuantity());
        current.setQuantity(requestedQuantity);
        current.setDiscountAmount(newDiscount);
        current.setLineTotal(lineTotal(current.getUnitPrice(), requestedQuantity, newDiscount));
    }

    private void swapLine(
            OrderItem oldLine, OrderModificationRequestItem requested, List<SalesInvoiceItem> affectedInvoiceItems,
            Order order, OrderModificationRequest request, UUID actorId
    ) {
        CatalogSkuSummaryDto replacement = catalogApi.getSkuSummary(requested.getRequestedVariantId())
                .filter(sku -> sku.status() == null || "ACTIVE".equalsIgnoreCase(sku.status()))
                .orElseThrow(() -> new AppException(OrderModificationErrorCode.REPLACEMENT_SKU_NOT_FOUND));
        BigDecimal quantity = oldLine.getQuantity();
        List<StockReservationService.Reservation> oldReservations = reservations.findByOrder(order.getOrderId()).stream()
                .filter(reservation -> oldLine.getOrderItemId().equals(reservation.orderItemId())).toList();
        releaseAllHeldReservations(oldReservations, request, actorId);
        oldLine.setQuantity(BigDecimal.ZERO);
        oldLine.setDiscountAmount(BigDecimal.ZERO);
        oldLine.setLineTotal(BigDecimal.ZERO);

        OrderItem replacementLine = orderItemRepository.save(OrderItem.builder()
                .orderItemId(UUID.randomUUID()).orderId(order.getOrderId()).variantId(replacement.variantId())
                .sku(replacement.sku()).productName(replacement.productName() != null
                        ? replacement.productName() : replacement.variantName())
                .variantName(replacement.variantName()).quantity(quantity).unitPrice(amount(replacement.sellingPrice()))
                .discountAmount(BigDecimal.ZERO)
                .lineTotal(lineTotal(amount(replacement.sellingPrice()), quantity, BigDecimal.ZERO)).build());
        for (SalesInvoiceItem invoiceItem : affectedInvoiceItems) {
            invoiceItem.setOrderItemId(replacementLine.getOrderItemId());
            invoiceItem.setVariantId(replacement.variantId());
            invoiceItem.setDescription(description(replacementLine));
            invoiceItem.setUnitPrice(amount(replacement.sellingPrice()));
            invoiceItem.setLineTotal(lineTotal(invoiceItem.getUnitPrice(), invoiceItem.getQuantity(), invoiceItem.getDiscountAmount()));
        }
        reallocateReleasedReservations(oldReservations, replacementLine, quantity, request, actorId);
    }

    private void releaseHeldReservations(
            UUID orderItemId, BigDecimal requestedRelease, OrderModificationRequest request, UUID actorId
    ) {
        BigDecimal remaining = requestedRelease;
        for (StockReservationService.Reservation reservation : reservations.findByOrder(request.getOrderId())) {
            if (!orderItemId.equals(reservation.orderItemId()) || remaining.signum() == 0) {
                continue;
            }
            BigDecimal release = reservation.heldQty().min(remaining);
            if (release.signum() > 0) {
                reservations.release(new StockReservationService.Release(
                        deterministicId(request, "release", reservation.reservationId()), reservation.reservationId(), release,
                        "Approved UC-26 item reduction " + request.getModificationRequestId(), actorId));
                remaining = remaining.subtract(release);
            }
        }
    }

    private void releaseAllHeldReservations(
            Collection<StockReservationService.Reservation> reservationsToRelease, OrderModificationRequest request, UUID actorId
    ) {
        for (StockReservationService.Reservation reservation : reservationsToRelease) {
            if (reservation.heldQty().signum() > 0) {
                reservations.release(new StockReservationService.Release(
                        deterministicId(request, "swap-release", reservation.reservationId()), reservation.reservationId(),
                        reservation.heldQty(), "Approved UC-26 item swap " + request.getModificationRequestId(), actorId));
            }
        }
    }

    private void reallocateReleasedReservations(
            List<StockReservationService.Reservation> oldReservations, OrderItem replacementLine, BigDecimal requestedQuantity,
            OrderModificationRequest request, UUID actorId
    ) {
        for (StockReservationService.Reservation oldReservation : oldReservations) {
            reservations.reserve(new StockReservationService.Reserve(
                    deterministicId(request, "swap-reserve", oldReservation.reservationId()), replacementLine.getOrderId(),
                    replacementLine.getOrderItemId(), replacementLine.getVariantId(), oldReservation.warehouseCode(),
                    requestedQuantity, actorId));
        }
    }

    private void recalculateDraftInvoicesAndOrder(Order order, List<SalesInvoice> invoices, List<UUID> invoiceIds) {
        Map<UUID, List<SalesInvoiceItem>> itemsByInvoice = invoiceItemRepository.findByInvoiceIdInForUpdate(invoiceIds).stream()
                .collect(Collectors.groupingBy(SalesInvoiceItem::getInvoiceId));
        for (SalesInvoice invoice : invoices) {
            if (SalesInvoiceStatus.CANCELLED.name().equalsIgnoreCase(invoice.getStatus())) {
                continue;
            }
            List<SalesInvoiceItem> items = itemsByInvoice.getOrDefault(invoice.getInvoiceId(), List.of());
            if (items.isEmpty()) {
                throw new AppException(OrderModificationErrorCode.INVALID_ITEM_CHANGE);
            }
            BigDecimal subtotal = items.stream().map(item -> amount(item.getLineTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tax = items.stream().map(item -> amount(item.getLineTotal()).multiply(amount(item.getTaxRate()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal discount = amount(invoice.getDiscountAmount());
            invoice.setSubtotal(subtotal);
            invoice.setTaxAmount(tax);
            invoice.setTotalAmount(subtotal.subtract(discount).max(BigDecimal.ZERO).add(tax));
        }
        List<OrderItem> currentItems = orderItemRepository.findByOrderIdForUpdate(order.getOrderId());
        BigDecimal subtotal = currentItems.stream().map(item -> amount(item.getLineTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invoiceDiscount = invoices.stream().filter(invoice -> !SalesInvoiceStatus.CANCELLED.name().equalsIgnoreCase(invoice.getStatus()))
                .map(invoice -> amount(invoice.getDiscountAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = invoices.stream().filter(invoice -> !SalesInvoiceStatus.CANCELLED.name().equalsIgnoreCase(invoice.getStatus()))
                .map(invoice -> amount(invoice.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(amount(order.getShippingFee()));
        order.setSubtotal(subtotal);
        order.setDiscountAmount(currentItems.stream().map(item -> amount(item.getDiscountAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).add(invoiceDiscount));
        order.setTotalAmount(total);
        if (order.getRemainingDebtAmount() != null || order.getDepositAmount() != null) {
            order.setRemainingDebtAmount(total.subtract(amount(order.getDepositAmount())).max(BigDecimal.ZERO));
        }
    }

    private void createRefundWhenRequired(
            OrderModificationRequest request, Order order, BigDecimal verifiedAmount, UUID actorId
    ) {
        BigDecimal newTotal = proposedTotal(request, order.getTotalAmount());
        if (newTotal.compareTo(verifiedAmount) < 0 && request.getRefundTransactionId() == null) {
            request.setRefundTransactionId(payments.createPendingCustomerRefund(new PendingCustomerRefundCommand(
                    request.getCustomerId(), order.getOrderId(), request.getOriginalInvoiceId(),
                    verifiedAmount.subtract(newTotal), "UC-26 request " + request.getModificationRequestId(), actorId)));
        }
    }

    private boolean refundIsStillPending(OrderModificationRequest request) {
        return request.getRefundTransactionId() != null && !payments.findCustomerRefund(request.getRefundTransactionId())
                .orElseThrow(() -> new AppException(OrderModificationErrorCode.REFUND_NOT_FOUND))
                .status().equalsIgnoreCase("VERIFIED");
    }

    private OrderModificationRequest lockReviewable(UUID id) {
        OrderModificationRequest request = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(OrderModificationErrorCode.REQUEST_NOT_FOUND));
        if (request.getRequestStatus() != OrderModificationRequestStatus.PENDING_REVIEW) {
            throw new AppException(OrderModificationErrorCode.INVALID_REQUEST_STATE);
        }
        return request;
    }

    private Order requireEligible(OrderModificationRequest request) {
        Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new AppException(OrderModificationErrorCode.ORDER_NOT_ELIGIBLE));
        boolean dispatched = deliveries.lockByOrderId(order.getOrderId()).stream()
                .map(delivery -> delivery.status() == null ? "" : delivery.status().toUpperCase())
                .anyMatch(status -> List.of("DISPATCHED", "IN_TRANSIT", "DELIVERED").contains(status));
        if (!OrderStatus.PROCESSING.name().equalsIgnoreCase(order.getOrderStatus()) || dispatched) {
            throw new AppException(OrderModificationErrorCode.ORDER_NOT_ELIGIBLE);
        }
        return order;
    }

    private BigDecimal proposedTotal(OrderModificationRequest request, BigDecimal currentTotal) {
        if (request.getRequestType() == OrderModificationRequestType.ADDRESS_CHANGE) {
            return amount(currentTotal);
        }
        if (request.getRequestType() == OrderModificationRequestType.FULL_CANCELLATION) {
            return BigDecimal.ZERO;
        }
        BigDecimal delta = BigDecimal.ZERO;
        for (OrderModificationRequestItem item : requestItemRepository
                .findByModificationRequestIdOrderByCreatedAtAsc(request.getModificationRequestId())) {
            if (item.getChangeAction() == OrderModificationItemAction.REMOVE) {
                delta = delta.subtract(item.getUnitPriceSnapshot().multiply(item.getOriginalQuantity()));
            } else if (item.getChangeAction() == OrderModificationItemAction.REDUCE) {
                delta = delta.subtract(item.getUnitPriceSnapshot()
                        .multiply(item.getOriginalQuantity().subtract(item.getRequestedQuantity())));
            } else if (item.getChangeAction() == OrderModificationItemAction.SWAP) {
                CatalogSkuSummaryDto sku = catalogApi.getSkuSummary(item.getRequestedVariantId())
                        .orElseThrow(() -> new AppException(OrderModificationErrorCode.REPLACEMENT_SKU_NOT_FOUND));
                delta = delta.add(amount(sku.sellingPrice()).subtract(item.getUnitPriceSnapshot())
                        .multiply(item.getOriginalQuantity()));
            }
        }
        return amount(currentTotal).add(delta).max(BigDecimal.ZERO);
    }

    private OrderModificationRequest find(UUID id) {
        return requestRepository.findById(id).orElseThrow(() -> new AppException(OrderModificationErrorCode.REQUEST_NOT_FOUND));
    }

    private boolean hasTaxInvoiceLink(SalesInvoice invoice) {
        return invoice.getProviderInvoiceId() != null && !invoice.getProviderInvoiceId().isBlank()
                && invoice.getSignedAt() != null;
    }

    private void cancelOrder(Order order, OrderModificationRequest request, UUID actorId) {
        for (StockReservationService.Reservation reservation : reservations.findByOrder(order.getOrderId())) {
            if (reservation.heldQty().signum() > 0) {
                reservations.release(new StockReservationService.Release(
                        deterministicId(request, "cancel", reservation.reservationId()), reservation.reservationId(),
                        reservation.heldQty(), "Approved UC-26 cancellation " + request.getModificationRequestId(), actorId));
            }
        }
        for (SalesInvoice invoice : invoiceRepository.findByOrderIdForUpdate(order.getOrderId())) {
            if (SalesInvoiceStatus.DRAFT.name().equalsIgnoreCase(invoice.getStatus())) {
                invoice.setStatus(SalesInvoiceStatus.CANCELLED.name());
                invoice.setCancelledBy(actorId);
                invoice.setCancelledAt(OffsetDateTime.now());
            }
        }
        order.setOrderStatus(OrderStatus.CANCELLED.name());
    }

    private OrderModificationRequestResponse toResponse(OrderModificationRequest request) {
        OrderModificationRequestResponse response = mapper.toResponse(request);
        response.setItems(mapper.toItemResponses(requestItemRepository
                .findByModificationRequestIdOrderByCreatedAtAsc(request.getModificationRequestId())));
        return response;
    }

    private OrderModificationRequestResponse toResponseWithItems(OrderModificationRequest request,
                                                                  List<OrderModificationRequestItem> items) {
        OrderModificationRequestResponse response = mapper.toResponse(request);
        response.setItems(mapper.toItemResponses(items));
        return response;
    }

    private void audit(UUID actorId, OrderModificationRequest request, String action,
                       OrderModificationRequestStatus oldStatus, OrderModificationRequestStatus newStatus, String reason) {
        events.publishEvent(new OrderModificationRequestReviewedEvent(
                UUID.randomUUID(), actorId, request.getModificationRequestId(), request.getOrderId(), action,
                Map.of("status", oldStatus.name()),
                Map.of("status", newStatus.name(), "requestType", request.getRequestType().name()), reason, Instant.now()));
    }

    private static OrderModificationRequestType parseRequestType(String value) {
        try {
            return OrderModificationRequestType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AppException(OrderModificationErrorCode.INVALID_REQUESTED_ITEM);
        }
    }

    private static OrderModificationItemAction parseItemAction(String value) {
        try {
            return OrderModificationItemAction.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AppException(OrderModificationErrorCode.INVALID_REQUESTED_ITEM);
        }
    }

    private static UUID deterministicId(OrderModificationRequest request, String operation, UUID resourceId) {
        return UUID.nameUUIDFromBytes(("uc26:" + request.getModificationRequestId() + ":" + operation + ":" + resourceId)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static BigDecimal proportionalDiscount(BigDecimal discount, BigDecimal retained, BigDecimal original) {
        return original == null || original.signum() == 0 ? BigDecimal.ZERO
                : amount(discount).multiply(retained).divide(original, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal lineTotal(BigDecimal unitPrice, BigDecimal quantity, BigDecimal discount) {
        return amount(unitPrice).multiply(amount(quantity)).subtract(amount(discount)).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String description(OrderItem line) {
        if (line.getProductName() == null) {
            return line.getVariantName();
        }
        return line.getVariantName() == null || line.getVariantName().isBlank()
                ? line.getProductName() : line.getProductName() + " - " + line.getVariantName();
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
