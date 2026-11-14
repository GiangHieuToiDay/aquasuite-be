package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.sales.api.SalesInvoiceApi;
import com.aquasuite.ims.module.sales.api.dto.SalesInvoiceSummaryDto;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoiceItem;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.enums.SalesInvoiceStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai public contract {@link SalesInvoiceApi} để module Fulfillment tra cứu hóa đơn ISSUED
 * khi tạo phiếu xuất kho và thông báo hoàn tất đơn hàng sau khi xuất kho xong.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesInvoiceApiImpl implements SalesInvoiceApi {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceItemRepository salesInvoiceItemRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesInvoiceSummaryDto> getInvoiceById(UUID invoiceId) {
        if (invoiceId == null) {
            return Optional.empty();
        }
        return salesInvoiceRepository.findById(invoiceId).map(inv -> {
            String orderCode = orderRepository.findById(inv.getOrderId())
                    .map(Order::getOrderCode)
                    .orElse(null);
            List<SalesInvoiceItem> items = salesInvoiceItemRepository.findByInvoiceIdOrderByInvoiceItemIdAsc(invoiceId);
            return toSummary(inv, orderCode, items);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceSummaryDto> getInvoicesByOrderId(UUID orderId) {
        if (orderId == null) {
            return List.of();
        }
        List<SalesInvoice> invoices = salesInvoiceRepository.findByOrderIdOrderByBatchNoAscCreatedAtAsc(orderId);
        return mapInvoices(orderId, invoices);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceSummaryDto> getIssuedInvoicesByOrderId(UUID orderId) {
        if (orderId == null) {
            return List.of();
        }
        List<SalesInvoice> invoices = salesInvoiceRepository
                .findByOrderIdAndStatusOrderByBatchNoAscCreatedAtAsc(orderId, SalesInvoiceStatus.ISSUED.name());
        return mapInvoices(orderId, invoices);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean areAllActiveInvoicesIssued(UUID orderId) {
        if (orderId == null) {
            return false;
        }
        List<SalesInvoice> activeInvoices = salesInvoiceRepository.findByOrderIdOrderByBatchNoAscCreatedAtAsc(orderId)
                .stream()
                .filter(inv -> !SalesInvoiceStatus.CANCELLED.name().equalsIgnoreCase(inv.getStatus()))
                .toList();
        if (activeInvoices.isEmpty()) {
            return false;
        }
        return activeInvoices.stream()
                .allMatch(inv -> SalesInvoiceStatus.ISSUED.name().equalsIgnoreCase(inv.getStatus()));
    }

    @Override
    @Transactional
    public void completeOrderIfEligibleAfterDispatch(UUID orderId) {
        if (orderId == null) {
            return;
        }
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        if (!OrderStatus.PROCESSING.name().equalsIgnoreCase(order.getOrderStatus())) {
            return;
        }
        if (!areAllActiveInvoicesIssued(orderId)) {
            log.debug("Order {} still has non-ISSUED invoices; skipping auto-complete after dispatch",
                    order.getOrderCode());
            return;
        }
        if (!PaymentStatus.PAID.name().equalsIgnoreCase(order.getPaymentStatus())) {
            log.debug("Order {} has paymentStatus {}; waiting for PAID before auto-complete",
                    order.getOrderCode(), order.getPaymentStatus());
            return;
        }

        order.setOrderStatus(OrderStatus.COMPLETED.name());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);
        log.info("Order {} auto-marked COMPLETED after all ISSUED invoices were dispatched", order.getOrderCode());
    }

    private List<SalesInvoiceSummaryDto> mapInvoices(UUID orderId, List<SalesInvoice> invoices) {
        if (invoices.isEmpty()) {
            return List.of();
        }
        String orderCode = orderRepository.findById(orderId).map(Order::getOrderCode).orElse(null);
        List<UUID> invoiceIds = invoices.stream().map(SalesInvoice::getInvoiceId).toList();
        Map<UUID, List<SalesInvoiceItem>> itemsMap = salesInvoiceItemRepository.findByInvoiceIdIn(invoiceIds)
                .stream()
                .collect(Collectors.groupingBy(SalesInvoiceItem::getInvoiceId));
        return invoices.stream()
                .map(inv -> toSummary(inv, orderCode, itemsMap.getOrDefault(inv.getInvoiceId(), List.of())))
                .toList();
    }

    private static SalesInvoiceSummaryDto toSummary(
            SalesInvoice inv,
            String orderCode,
            List<SalesInvoiceItem> items
    ) {
        List<SalesInvoiceSummaryDto.SalesInvoiceItemSummaryDto> itemDtos = items.stream()
                .map(it -> new SalesInvoiceSummaryDto.SalesInvoiceItemSummaryDto(
                        it.getInvoiceItemId(),
                        it.getOrderItemId(),
                        it.getVariantId(),
                        it.getDescription(),
                        it.getQuantity(),
                        it.getUnitPrice(),
                        it.getDiscountAmount(),
                        it.getLineTotal()))
                .toList();
        return new SalesInvoiceSummaryDto(
                inv.getInvoiceId(),
                inv.getInvoiceCode(),
                inv.getOrderId(),
                orderCode,
                inv.getCustomerId(),
                inv.getInvoiceType(),
                inv.getStatus(),
                inv.getBatchNo(),
                inv.getBatchName(),
                inv.getIssueDate(),
                inv.getDueDate(),
                inv.getTotalAmount(),
                inv.getIssuedAt(),
                itemDtos
        );
    }
}

