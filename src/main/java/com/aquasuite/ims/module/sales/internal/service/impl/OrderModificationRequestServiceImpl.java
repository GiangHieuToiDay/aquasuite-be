package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.finance.api.PaymentSettlementApi;
import com.aquasuite.ims.module.finance.api.dto.PendingCustomerRefundCommand;
import com.aquasuite.ims.module.fulfillment.api.OrderDeliveryQueryApi;
import com.aquasuite.ims.module.inventory.api.StockReservationApi;
import com.aquasuite.ims.module.sales.api.event.OrderModificationRequestReviewedEvent;
import com.aquasuite.ims.module.sales.api.event.OrderModificationResultEmailRequestedEvent;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequestItem;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoiceItem;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationItemAction;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestType;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.SalesInvoiceStatus;
import com.aquasuite.ims.module.sales.internal.error.OrderModificationErrorCode;
import com.aquasuite.ims.module.sales.internal.mapper.OrderModificationRequestMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.ApproveOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.RecordOrderModificationTaxResultRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.ReviewOrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderModificationRequestResponse;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderModificationRequestItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderModificationRequestRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import com.aquasuite.ims.module.sales.internal.service.OrderModificationRequestService;
import com.aquasuite.ims.share.exception.AppException;
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
public class OrderModificationRequestServiceImpl implements OrderModificationRequestService {
    private final OrderModificationRequestRepository requestRepository;
    private final OrderModificationRequestItemRepository requestItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final SalesInvoiceItemRepository invoiceItemRepository;
    private final OrderModificationRequestMapper mapper;
    private final OrderDeliveryQueryApi deliveries;
    private final PaymentSettlementApi payments;
    private final CatalogApi catalogApi;
    private final StockReservationApi reservations;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public Page<OrderModificationRequestResponse> list(Pageable page, boolean accountantOnly) {
        Page<OrderModificationRequest> requests = accountantOnly
                ? requestRepository.findByRequestStatusOrderByCreatedAtDesc(OrderModificationRequestStatus.PENDING_CANCEL, page)
                : requestRepository.findAllByOrderByCreatedAtDesc(page);
        return requests.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderModificationRequestResponse detail(UUID id) {
        return toResponse(find(id));
    }

    @Override
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

    @Override
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

        if (request.getRequestType() == OrderModificationRequestType.ITEM_CHANGE
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
            invoiceRepository.findElectronicallyIssuedStoreInvoicesByOrderId(order.getOrderId()).stream()
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

    @Override
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

        OrderModificationRequestStatus previous = request.getRequestStatus();
        if (request.getRequestType() == OrderModificationRequestType.FULL_CANCELLATION) {
            Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                    .orElseThrow(() -> new AppException(OrderModificationErrorCode.ORDER_NOT_ELIGIBLE));
            cancelOrder(order, request, actorId);
        }
        request.setRequestStatus(OrderModificationRequestStatus.APPROVED);
        audit(actorId, request, "TAX_RESULT_RECORDED", previous, request.getRequestStatus(), request.getTaxResultReference());
        return toResponse(request);
    }

    @Override
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
        List<StockReservationApi.Reservation> oldReservations = reservations.findByOrder(order.getOrderId()).stream()
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
        for (StockReservationApi.Reservation reservation : reservations.findByOrder(request.getOrderId())) {
            if (!orderItemId.equals(reservation.orderItemId()) || remaining.signum() == 0) {
                continue;
            }
            BigDecimal release = reservation.heldQty().min(remaining);
            if (release.signum() > 0) {
                reservations.release(new StockReservationApi.Release(
                        deterministicId(request, "release", reservation.reservationId()), reservation.reservationId(), release,
                        "Approved UC-26 item reduction " + request.getModificationRequestId(), actorId));
                remaining = remaining.subtract(release);
            }
        }
    }

    private void releaseAllHeldReservations(
            Collection<StockReservationApi.Reservation> reservationsToRelease, OrderModificationRequest request, UUID actorId
    ) {
        for (StockReservationApi.Reservation reservation : reservationsToRelease) {
            if (reservation.heldQty().signum() > 0) {
                reservations.release(new StockReservationApi.Release(
                        deterministicId(request, "swap-release", reservation.reservationId()), reservation.reservationId(),
                        reservation.heldQty(), "Approved UC-26 item swap " + request.getModificationRequestId(), actorId));
            }
        }
    }

    private void reallocateReleasedReservations(
            List<StockReservationApi.Reservation> oldReservations, OrderItem replacementLine, BigDecimal requestedQuantity,
            OrderModificationRequest request, UUID actorId
    ) {
        for (StockReservationApi.Reservation oldReservation : oldReservations) {
            reservations.reserve(new StockReservationApi.Reserve(
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
        for (StockReservationApi.Reservation reservation : reservations.findByOrder(order.getOrderId())) {
            if (reservation.heldQty().signum() > 0) {
                reservations.release(new StockReservationApi.Release(
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

    private void audit(UUID actorId, OrderModificationRequest request, String action,
                       OrderModificationRequestStatus oldStatus, OrderModificationRequestStatus newStatus, String reason) {
        events.publishEvent(new OrderModificationRequestReviewedEvent(
                UUID.randomUUID(), actorId, request.getModificationRequestId(), request.getOrderId(), action,
                Map.of("status", oldStatus.name()),
                Map.of("status", newStatus.name(), "requestType", request.getRequestType().name()), reason, Instant.now()));
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
