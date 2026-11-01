package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.RecordOrderPaymentCommand;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Triển khai nghiệp vụ nội bộ cho SalesOrderApi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderApiImpl implements SalesOrderApi {

    private final OrderRepository orderRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;

    @Override
    @Transactional
    public void recordOrderPayment(RecordOrderPaymentCommand command) {
        if (command == null || command.orderId() == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        BigDecimal currentPaid = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaid = currentPaid.add(command.amount());
        order.setPaidAmount(newPaid);

        // Tự động phân giải trạng thái thanh toán mới
        if (newPaid.compareTo(order.getTotalAmount()) >= 0) {
            order.setPaymentStatus(PaymentStatus.PAID.name());
        } else {
            order.setPaymentStatus(PaymentStatus.PARTIALLY_PAID.name());
        }

        orderRepository.save(order);

        // Đồng bộ trạng thái hóa đơn bán lẻ
        salesInvoiceRepository.findByOrderId(order.getOrderId()).ifPresent(invoice -> {
            invoice.setStatus(order.getPaymentStatus());
            salesInvoiceRepository.save(invoice);
        });

        log.info("Recorded payment of {} for order {}. New paidAmount={}, paymentStatus={}",
                command.amount(), order.getOrderCode(), newPaid, order.getPaymentStatus());
    }
}
