package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderMilestoneDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderPaymentMilestone;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.repository.OrderPaymentMilestoneRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
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
    /** Đọc các mốc thanh toán theo thỏa thuận đã snapshot vào đơn. */
    private final OrderPaymentMilestoneRepository orderPaymentMilestoneRepository;

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
                    .depositAmount(order.getDepositAmount())
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

    /**
     * Trả snapshot các mốc thanh toán của đơn theo thứ tự ổn định cho UC-23.
     *
     * <p>Phương thức chỉ đọc, dùng {@code sortOrder} tăng dần làm thứ tự chính và
     * {@code milestoneId} làm tiêu chí phụ khi trùng thứ tự để kết quả xác định
     * (deterministic) giữa các lần đọc. Không tính tiền đã trả tại đây; Finance tính
     * tiến độ từ các giao dịch đã xác minh.</p>
     *
     * @param orderId mã đơn cần lấy mốc; {@code null} trả về danh sách rỗng
     * @return danh sách mốc đã sắp thứ tự, rỗng nếu đơn không có mốc
     */
    @Override
    @Transactional(readOnly = true)
    public List<SalesOrderMilestoneDto> getOrderPaymentMilestones(UUID orderId) {
        if (orderId == null) {
            return List.of();
        }
        return orderPaymentMilestoneRepository.findByOrderIdOrderBySortOrderAsc(orderId).stream()
                .sorted(Comparator
                        .comparing((OrderPaymentMilestone m) ->
                                m.getSortOrder() != null ? m.getSortOrder() : Integer.MAX_VALUE)
                        .thenComparing(OrderPaymentMilestone::getMilestoneId))
                .map(m -> SalesOrderMilestoneDto.builder()
                        .milestoneId(m.getMilestoneId())
                        .orderId(m.getOrderId())
                        .stageName(m.getStageName())
                        .percent(m.getPercent())
                        .amount(m.getAmount())
                        .sortOrder(m.getSortOrder())
                        .build())
                .toList();
    }
}
