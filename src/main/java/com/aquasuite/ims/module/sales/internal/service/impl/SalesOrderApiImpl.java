package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
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

    /** Đọc, khóa và cập nhật đơn hàng do Sales quản lý. */
    private final OrderRepository orderRepository;
    /** Tìm hóa đơn cửa hàng liên quan đến đơn. */
    private final SalesInvoiceRepository salesInvoiceRepository;

    /** Lấy thông tin đơn và mã hóa đơn cửa hàng phục vụ hiển thị, tính thanh toán. */
    @Override
    @Transactional(readOnly = true)
    public Optional<SalesOrderPaymentInfoDto> getOrderPaymentInfo(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orderRepository.findById(orderId).map(this::paymentInfo);
    }

    /** Khóa dòng đơn trong transaction hiện tại trước khi Finance ghi tiền. */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<SalesOrderPaymentInfoDto> lockOrderPaymentInfo(UUID orderId) {
        return orderRepository.findForPaymentUpdate(orderId).map(this::paymentInfo);
    }

    /** Chuyển dữ liệu đơn thành contract công khai; đơn đã hủy không được nhận thanh toán. */
    private SalesOrderPaymentInfoDto paymentInfo(Order order) {
            boolean isValid = !OrderStatus.CANCELLED.name().equalsIgnoreCase(order.getOrderStatus());
            return SalesOrderPaymentInfoDto.builder()
                    .orderId(order.getOrderId())
                    .orderCode(order.getOrderCode())
                    .customerId(order.getCustomerId())
                    .storeInvoiceId(salesInvoiceRepository.findByOrderIdAndInvoiceType(order.getOrderId(), "STORE")
                            .map(com.aquasuite.ims.module.sales.internal.entity.SalesInvoice::getInvoiceId).orElse(null))
                    .orderType(order.getOrderType())
                    .orderChannel(order.getOrderChannel())
                    .orderStatus(order.getOrderStatus())
                    .paymentStatus(order.getPaymentStatus())
                    .totalAmount(order.getTotalAmount())
                    .validForPayment(isValid)
                    .build();
    }

    /**
     * Ghi trạng thái do Finance tính lại, trong transaction đã khóa đơn.
     * @param orderId mã đơn cần cập nhật
     * @param paymentStatus trạng thái thanh toán hợp lệ
     * @throws AppException nếu trạng thái không hợp lệ hoặc không tìm thấy đơn
     */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
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

        Order order = orderRepository.findForPaymentUpdate(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        order.setPaymentStatus(resolvedStatus.name());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        log.info("Updated paymentStatus of order {} ({}) to {}",
                order.getOrderCode(), order.getOrderId(), resolvedStatus.name());
    }
}
