package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.enums.OrderChannel;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderType;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.service.impl.SalesOrderApiImpl;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderApiTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private SalesOrderApiImpl salesOrderApi;

    private UUID orderId;
    private UUID customerId;
    private Order order;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        order = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261103-0001")
                .customerId(customerId)
                .orderType(OrderType.RETAIL.name())
                .orderChannel(OrderChannel.IN_STORE.name())
                .orderStatus(OrderStatus.PROCESSING.name())
                .paymentStatus(PaymentStatus.UNPAID.name())
                .totalAmount(new BigDecimal("10000000"))
                .build();
    }

    @Test
    @DisplayName("Contract UC-23 (Bước 1): Lấy thông tin đơn hàng hợp lệ và tổng tiền cần thanh toán")
    void getOrderPaymentInfo_Success_ValidOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Optional<SalesOrderPaymentInfoDto> result = salesOrderApi.getOrderPaymentInfo(orderId);

        assertThat(result).isPresent();
        assertThat(result.get().orderId()).isEqualTo(orderId);
        assertThat(result.get().orderCode()).isEqualTo("SO-20261103-0001");
        assertThat(result.get().totalAmount()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(result.get().paymentStatus()).isEqualTo("UNPAID");
        assertThat(result.get().validForPayment()).isTrue();
    }

    @Test
    @DisplayName("Contract UC-23 (Bước 1): Đơn hàng đã hủy (CANCELLED) -> validForPayment = false")
    void getOrderPaymentInfo_CancelledOrder_ReturnsInvalidForPayment() {
        order.setOrderStatus(OrderStatus.CANCELLED.name());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Optional<SalesOrderPaymentInfoDto> result = salesOrderApi.getOrderPaymentInfo(orderId);

        assertThat(result).isPresent();
        assertThat(result.get().validForPayment()).isFalse();
    }

    @Test
    @DisplayName("Contract UC-23 (Bước 4): Cập nhật trạng thái thanh toán của đơn sang PARTIALLY_PAID hoặc PAID")
    void updatePaymentStatus_Success() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Khi khách trả 3 triệu tiền mặt + 7 triệu chuyển khoản Pending -> UC-23 cập nhật PARTIALLY_PAID
        salesOrderApi.updatePaymentStatus(orderId, "PARTIALLY_PAID");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID.name());

        // 2. Khi kế toán xác minh 7 triệu chuyển khoản ở UC-70 -> cập nhật PAID
        salesOrderApi.updatePaymentStatus(orderId, "PAID");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID.name());

        verify(orderRepository, org.mockito.Mockito.times(2)).save(order);
    }

    @Test
    @DisplayName("Contract UC-23: Ném lỗi khi cập nhật paymentStatus không hợp lệ")
    void updatePaymentStatus_InvalidStatus_ThrowsAppException() {
        assertThatThrownBy(() -> salesOrderApi.updatePaymentStatus(orderId, "UNKNOWN_STATUS"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.INVALID_PAYMENT_STATUS);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Contract UC-23: Ném lỗi khi đơn hàng không tồn tại")
    void updatePaymentStatus_OrderNotFound_ThrowsAppException() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salesOrderApi.updatePaymentStatus(orderId, "PAID"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.ORDER_NOT_FOUND);
    }
}
