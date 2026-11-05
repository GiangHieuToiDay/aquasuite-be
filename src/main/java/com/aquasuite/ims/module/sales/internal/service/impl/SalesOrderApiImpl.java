package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai nghiệp vụ nội bộ cho SalesOrderApi (cung cấp contract cho UC-23 & UC-70).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderApiImpl implements SalesOrderApi {

    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesOrderPaymentInfoDto> getOrderPaymentInfo(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orderRepository.findById(orderId).map(order -> {
            boolean isValid = !OrderStatus.CANCELLED.name().equalsIgnoreCase(order.getOrderStatus());
            return SalesOrderPaymentInfoDto.builder()
                    .orderId(order.getOrderId())
                    .orderCode(order.getOrderCode())
                    .customerId(order.getCustomerId())
                    .orderType(order.getOrderType())
                    .orderChannel(order.getOrderChannel())
                    .orderStatus(order.getOrderStatus())
                    .paymentStatus(order.getPaymentStatus())
                    .totalAmount(order.getTotalAmount())
                    .validForPayment(isValid)
                    .build();
        });
    }

    @Override
    @Transactional
    public void updatePaymentStatus(UUID orderId, String paymentStatus) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }
        if (paymentStatus == null || paymentStatus.isBlank()) {
            throw new AppException(SalesErrorCode.INVALID_PAYMENT_STATUS, String.valueOf(paymentStatus));
        }

        PaymentStatus resolvedStatus;
        try {
            resolvedStatus = PaymentStatus.valueOf(paymentStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(SalesErrorCode.INVALID_PAYMENT_STATUS, paymentStatus);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        order.setPaymentStatus(resolvedStatus.name());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        log.info("Updated paymentStatus of order {} ({}) to {}",
                order.getOrderCode(), order.getOrderId(), resolvedStatus.name());
    }
}
