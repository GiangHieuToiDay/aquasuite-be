package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.sales.api.SalesWarrantyApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderItemWarrantySnapshotDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderWarrantySummaryDto;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesWarrantyApiImpl implements SalesWarrantyApi {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SalesOrderItemWarrantySnapshotDto> lockOrderItemForWarranty(UUID orderItemId) {
        if (orderItemId == null) {
            return Optional.empty();
        }
        Optional<OrderItem> itemOpt = orderItemRepository.findByIdForUpdate(orderItemId);
        if (itemOpt.isEmpty()) {
            return Optional.empty();
        }
        OrderItem item = itemOpt.get();
        Order order = orderRepository.findById(item.getOrderId()).orElse(null);
        if (order == null) {
            return Optional.empty();
        }
        return Optional.of(SalesOrderItemWarrantySnapshotDto.builder()
                .orderItemId(item.getOrderItemId())
                .orderId(order.getOrderId())
                .orderCode(order.getOrderCode())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .orderDate(order.getOrderDate())
                .customerId(order.getCustomerId())
                .deliveryAddress(order.getDeliveryAddress())
                .variantId(item.getVariantId())
                .quantity(item.getQuantity())
                .sku(item.getSku())
                .productName(item.getProductName())
                .variantName(item.getVariantName())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesOrderWarrantySummaryDto> getOrderSummaryWithInvoice(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orderRepository.findById(orderId).map(order -> {
            List<SalesInvoice> invoices = salesInvoiceRepository.findByOrderIdOrderByIssueDateDesc(orderId);
            SalesInvoice invoice = invoices.stream()
                    .filter(inv -> "STORE".equalsIgnoreCase(inv.getInvoiceType()))
                    .findFirst()
                    .orElse(invoices.isEmpty() ? null : invoices.get(0));

            return SalesOrderWarrantySummaryDto.builder()
                    .orderId(order.getOrderId())
                    .orderCode(order.getOrderCode())
                    .orderStatus(order.getOrderStatus())
                    .paymentStatus(order.getPaymentStatus())
                    .orderDate(order.getOrderDate())
                    .deliveryAddress(order.getDeliveryAddress())
                    .customerId(order.getCustomerId())
                    .invoiceId(invoice != null ? invoice.getInvoiceId() : null)
                    .invoiceCode(invoice != null ? invoice.getInvoiceCode() : null)
                    .invoiceType(invoice != null ? invoice.getInvoiceType() : null)
                    .invoiceStatus(invoice != null ? invoice.getStatus() : null)
                    .issueDate(invoice != null ? invoice.getIssueDate() : null)
                    .invoiceTotalAmount(invoice != null ? invoice.getTotalAmount() : null)
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> getOrderCodesByIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getOrderId, Order::getOrderCode, (a, b) -> a));
    }
}

