package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.api.dto.RecordOrderPaymentCommand;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderApiTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SalesInvoiceRepository salesInvoiceRepository;

    @InjectMocks
    private SalesOrderApiImpl salesOrderApi;

    private UUID orderId;
    private Order order;
    private SalesInvoice invoice;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        order = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261101-0001")
                .totalAmount(new BigDecimal("10000000"))
                .paidAmount(new BigDecimal("3000000"))
                .paymentStatus(PaymentStatus.PARTIALLY_PAID.name())
                .build();

        invoice = SalesInvoice.builder()
                .invoiceId(UUID.randomUUID())
                .orderId(orderId)
                .status("PARTIALLY_PAID")
                .build();
    }

    @Test
    @DisplayName("Ghi nhận thêm tiền cọc thành công - Vẫn còn thiếu (PARTIALLY_PAID)")
    void recordOrderPayment_Success_PartialPayment() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(salesInvoiceRepository.findByOrderId(orderId)).thenReturn(Optional.of(invoice));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordOrderPaymentCommand command = RecordOrderPaymentCommand.builder()
                .orderId(orderId)
                .amount(new BigDecimal("2000000"))
                .paymentMethod("BANK_TRANSFER")
                .note("Khách cọc thêm đợt 2")
                .build();

        salesOrderApi.recordOrderPayment(command);

        assertThat(order.getPaidAmount()).isEqualByComparingTo(new BigDecimal("5000000"));
        assertThat(order.getRemainingAmount()).isEqualByComparingTo(new BigDecimal("5000000"));
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID.name());
        assertThat(invoice.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID.name());

        verify(orderRepository).save(order);
        verify(salesInvoiceRepository).save(invoice);
    }

    @Test
    @DisplayName("Ghi nhận thanh toán đủ thành công - Chuyển sang PAID và tiền còn thiếu = 0")
    void recordOrderPayment_Success_FullPayment() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(salesInvoiceRepository.findByOrderId(orderId)).thenReturn(Optional.of(invoice));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordOrderPaymentCommand command = RecordOrderPaymentCommand.builder()
                .orderId(orderId)
                .amount(new BigDecimal("7000000")) // 3M + 7M = 10M
                .paymentMethod("CASH")
                .note("Tất toán khi giao hàng")
                .build();

        salesOrderApi.recordOrderPayment(command);

        assertThat(order.getPaidAmount()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(order.getRemainingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID.name());
        assertThat(invoice.getStatus()).isEqualTo(PaymentStatus.PAID.name());

        verify(orderRepository).save(order);
        verify(salesInvoiceRepository).save(invoice);
    }

    @Test
    @DisplayName("Ném lỗi khi đơn hàng không tồn tại")
    void recordOrderPayment_OrderNotFound_ThrowsException() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        RecordOrderPaymentCommand command = RecordOrderPaymentCommand.builder()
                .orderId(orderId)
                .amount(new BigDecimal("1000000"))
                .build();

        assertThatThrownBy(() -> salesOrderApi.recordOrderPayment(command))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.ORDER_NOT_FOUND);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném lỗi khi số tiền thanh toán không hợp lệ (null hoặc <= 0)")
    void recordOrderPayment_InvalidAmount_ThrowsException() {
        RecordOrderPaymentCommand commandZero = RecordOrderPaymentCommand.builder()
                .orderId(orderId)
                .amount(BigDecimal.ZERO)
                .build();

        assertThatThrownBy(() -> salesOrderApi.recordOrderPayment(commandZero))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment amount must be greater than zero");

        verify(orderRepository, never()).findById(any());
    }
}
