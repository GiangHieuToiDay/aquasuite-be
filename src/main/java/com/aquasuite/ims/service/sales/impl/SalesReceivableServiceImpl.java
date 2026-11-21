package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.service.sales.SalesReceivableService;
import com.aquasuite.ims.dto.sales.response.SalesInvoiceDebtDto;
import com.aquasuite.ims.dto.sales.response.SalesOrderDebtDto;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.SalesInvoice;
import com.aquasuite.ims.repository.sales.OrderRepository;
import com.aquasuite.ims.repository.sales.SalesInvoiceRepository;
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
public class SalesReceivableServiceImpl implements SalesReceivableService {
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
    public List<SalesInvoiceDebtDto> findElectronicallyIntegratedInvoicesByOrderId(UUID orderId) {
        if (orderId == null) {
            return List.of();
        }
        return salesInvoiceRepository.findElectronicallyIntegratedInvoicesByOrderId(orderId).stream()
                .map(this::toInvoiceDto)
                .toList();
    }

    @Override
    public List<SalesInvoiceDebtDto> findInvoicesByIds(Collection<UUID> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return List.of();
        }
        return salesInvoiceRepository.findByInvoiceIdIn(invoiceIds).stream()
                .map(this::toInvoiceDto)
                .toList();
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
                invoice.getProviderInvoiceId(),
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
