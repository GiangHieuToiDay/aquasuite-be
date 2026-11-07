package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.api.SalesReceivableApi;
import com.aquasuite.ims.module.sales.api.dto.SalesInvoiceDebtDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderDebtDto;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReceivableApiImpl implements SalesReceivableApi {
    private final OrderRepository orderRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;

    @Override
    public List<SalesOrderDebtDto> findOrdersByCustomerIds(Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return orderRepository.findByCustomerIdIn(customerIds).stream().map(this::toOrderDto).toList();
    }

    @Override
    public List<SalesInvoiceDebtDto> findInvoicesByCustomerIds(Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return salesInvoiceRepository.findByCustomerIdIn(customerIds).stream().map(this::toInvoiceDto).toList();
    }

    @Override
    public Optional<SalesOrderDebtDto> findOrderById(UUID orderId) {
        return orderRepository.findById(orderId).map(this::toOrderDto);
    }

    private SalesOrderDebtDto toOrderDto(Order order) {
        return new SalesOrderDebtDto(order.getOrderId(), order.getCustomerId(), order.getOrderCode(),
                order.getOrderChannel(), order.getOrderStatus(), order.getPaymentStatus(),
                order.getSubtotal(), order.getDiscountAmount(), order.getTotalAmount(), order.getDueDate());
    }

    private SalesInvoiceDebtDto toInvoiceDto(SalesInvoice invoice) {
        return new SalesInvoiceDebtDto(
                invoice.getInvoiceId(),
                invoice.getOrderId(),
                invoice.getCustomerId(),
                invoice.getInvoiceCode(),
                invoice.getInvoiceSymbol(),
                invoice.getIssueDate(),
                invoice.getSignedAt(),
                invoice.getCreditTermDays(),
                invoice.getDueDate(),
                invoice.getStatus(),
                invoice.getSubtotal(),
                invoice.getDiscountAmount(),
                invoice.getTaxAmount(),
                invoice.getTotalAmount(),
                invoice.getOriginalInvoiceId(),
                invoice.getInvoiceType()
        );
    }
}
