package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogBundleSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.finance.api.FinanceApi;
import com.aquasuite.ims.module.finance.api.dto.CreatePaymentTransactionCommand;
import com.aquasuite.ims.module.finance.api.dto.PaymentTransactionResponseDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockAdjustCommand;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoiceItem;
import com.aquasuite.ims.module.sales.internal.enums.FulfillmentMethod;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.PaymentMethod;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.mapper.SalesOrderMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.CancelOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateComboItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateOrderItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdatePreDispatchOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import com.aquasuite.ims.module.sales.internal.service.helper.OrderCodeGenerator;
import com.aquasuite.ims.module.sales.internal.service.impl.SalesOrderServiceImpl;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private SalesInvoiceRepository salesInvoiceRepository;

    @Mock
    private SalesInvoiceItemRepository salesInvoiceItemRepository;

    @Mock
    private CatalogApi catalogApi;

    @Mock
    private InventoryApi inventoryApi;

    @Mock
    private CrmApi crmApi;

    @Mock
    private IamApi iamApi;

    @Mock
    private FinanceApi financeApi;

    @Spy
    private SalesOrderMapper salesOrderMapper = Mappers.getMapper(SalesOrderMapper.class);

    @Mock
    private OrderCodeGenerator orderCodeGenerator;

    @InjectMocks
    private SalesOrderServiceImpl salesOrderService;

    private UUID customerId;
    private UUID staffId;
    private UUID variantId;
    private CustomerSummaryDto customerDto;
    private CatalogSkuSummaryDto skuDto;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        variantId = UUID.randomUUID();

        customerDto = CustomerSummaryDto.builder()
                .customerId(customerId)
                .customerCode("CUS-001")
                .customerName("Khách Hàng Thử Nghiệm")
                .phone("0988776655")
                .status("ACTIVE")
                .build();

        skuDto = CatalogSkuSummaryDto.builder()
                .variantId(variantId)
                .sku("SKU-PUMP-01")
                .productName("Máy bơm chìm hồ cá Aqua")
                .variantName("Công suất 50W")
                .sellingPrice(new BigDecimal("500000"))
                .status("ACTIVE")
                .build();

        lenient().when(salesInvoiceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(salesInvoiceItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(salesInvoiceRepository.countByInvoiceCodeStartingWith(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("Tạo đơn hàng POS IN_STORE_PICKUP (COMPLETED): Trừ trực tiếp on_hand_qty và ghi nhận thanh toán")
    void createSalesOrder_Success_InStorePickup_DeductsPhysicalStock() {
        // Arrange
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(customerId)
                .fulfillmentMethod(FulfillmentMethod.IN_STORE_PICKUP)
                .paymentMethod(PaymentMethod.CASH)
                .collectedAmount(new BigDecimal("1000000"))
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .variantId(variantId)
                                .quantity(new BigDecimal("2"))
                                .discountAmount(BigDecimal.ZERO)
                                .build()
                ))
                .build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(iamApi.getCurrentUserId()).thenReturn(staffId);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261031-0001");
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        when(inventoryApi.checkAvailability(variantId, new BigDecimal("2"))).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(financeApi.recordPaymentTransaction(any())).thenReturn(PaymentTransactionResponseDto.builder()
                .transactionId(UUID.randomUUID())
                .transactionCode("REC-20261031-0001")
                .amount(new BigDecimal("1000000"))
                .build());

        // Act
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("SO-20261031-0001");
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED.name());
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID.name());
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1000000"));

        // Xác nhận trừ kho thực tế on_hand_qty bằng adjustPhysicalStock, KHÔNG gọi reserveStock
        verify(inventoryApi).adjustPhysicalStock(any(StockAdjustCommand.class));
        verify(inventoryApi, never()).reserveStock(any(), any());

        // Xác nhận đã ghi nhận giao dịch thanh toán vào financeApi
        verify(financeApi).recordPaymentTransaction(any(CreatePaymentTransactionCommand.class));
    }

    @Test
    @DisplayName("Tạo đơn hàng SCHEDULED_DELIVERY (PROCESSING): Tăng reserved_qty và ghi nhận thanh toán đặt cọc")
    void createSalesOrder_Success_ScheduledDelivery_ReservesStock() {
        // Arrange
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(customerId)
                .fulfillmentMethod(FulfillmentMethod.SCHEDULED_DELIVERY)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .collectedAmount(new BigDecimal("400000")) // Cọc 1 phần
                .deliveryAddress("Số 10 Phố Huế, Hà Nội")
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .variantId(variantId)
                                .quantity(new BigDecimal("2"))
                                .discountAmount(BigDecimal.ZERO)
                                .build()
                ))
                .build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(iamApi.getCurrentUserId()).thenReturn(staffId);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261031-0002");
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        when(inventoryApi.checkAvailability(variantId, new BigDecimal("2"))).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING.name());
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID.name());

        // Xác nhận chỉ tăng reserved_qty, KHÔNG trừ on_hand_qty
        verify(inventoryApi).reserveStock(variantId, new BigDecimal("2"));
        verify(inventoryApi, never()).adjustPhysicalStock(any());

        // Ghi nhận giao dịch cọc vào finance
        verify(financeApi).recordPaymentTransaction(any(CreatePaymentTransactionCommand.class));
    }

    @Test
    @DisplayName("Tạo đơn hàng với gói Combo thành công")
    void createSalesOrder_Success_WithCombo() {
        // Arrange
        UUID comboId = UUID.randomUUID();
        UUID componentVariantId = UUID.randomUUID();

        CatalogBundleSummaryDto comboDto = CatalogBundleSummaryDto.builder()
                .bundleId(comboId)
                .bundleName("Combo Lọc Nước Hồ Koi")
                .packagePrice(new BigDecimal("1200000"))
                .status("ACTIVE")
                .items(List.of(
                        CatalogBundleSummaryDto.CatalogBundleItemDto.builder()
                                .variantId(componentVariantId)
                                .sku("SKU-FILTER-01")
                                .variantName("Bộ lọc tràn 3 tầng")
                                .quantity(1)
                                .sellingPrice(new BigDecimal("1200000"))
                                .build()
                ))
                .build();

        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(customerId)
                .fulfillmentMethod(FulfillmentMethod.IN_STORE_PICKUP)
                .paymentMethod(PaymentMethod.CASH)
                .collectedAmount(new BigDecimal("1200000"))
                .combos(List.of(
                        CreateComboItemRequest.builder()
                                .comboId(comboId)
                                .quantity(1)
                                .build()
                ))
                .build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(iamApi.getCurrentUserId()).thenReturn(staffId);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261031-0003");
        when(catalogApi.getBundleSummary(comboId)).thenReturn(Optional.of(comboDto));
        when(inventoryApi.checkAvailability(eq(componentVariantId), any(BigDecimal.class))).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("SO-20261031-0003");
        verify(inventoryApi).adjustPhysicalStock(any(StockAdjustCommand.class));
    }

    @Test
    @DisplayName("Báo lỗi khi số lượng tồn kho khả dụng không đủ (INSUFFICIENT_STOCK)")
    void createSalesOrder_InsufficientStock_ThrowsAppException() {
        // Arrange
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(customerId)
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .variantId(variantId)
                                .quantity(new BigDecimal("50"))
                                .build()
                ))
                .build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        when(inventoryApi.checkAvailability(variantId, new BigDecimal("50"))).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> salesOrderService.createSalesOrder(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.INSUFFICIENT_STOCK);

        verify(inventoryApi, never()).adjustPhysicalStock(any());
        verify(inventoryApi, never()).reserveStock(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chặn mua hàng công nợ nếu có hóa đơn quá hạn trên 60 ngày (CREDIT_LIMIT_EXCEEDED)")
    void createSalesOrder_CreditOverdue_ThrowsAppException() {
        // Arrange
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(customerId)
                .paymentMethod(PaymentMethod.CREDIT)
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .variantId(variantId)
                                .quantity(BigDecimal.ONE)
                                .build()
                ))
                .build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(orderRepository.hasOverdueUnpaidOrders(eq(customerId), any(LocalDate.class))).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> salesOrderService.createSalesOrder(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.CREDIT_LIMIT_EXCEEDED);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hủy đơn hàng đang xử lý (PROCESSING) và tự động giải phóng tồn kho đã đặt giữ")
    void cancelOrder_Success_ReleasesReservedStock() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261031-0005")
                .orderStatus(OrderStatus.PROCESSING.name())
                .note("Đang đóng hàng")
                .build();

        OrderItem item = OrderItem.builder()
                .orderItemId(UUID.randomUUID())
                .orderId(orderId)
                .variantId(variantId)
                .quantity(new BigDecimal("3"))
                .build();

        CancelOrderRequest request = CancelOrderRequest.builder()
                .cancelReason("Khách đổi ý không lấy nữa")
                .build();

        SalesInvoice invoice = SalesInvoice.builder()
                .invoiceId(UUID.randomUUID())
                .orderId(orderId)
                .status("PROCESSING")
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(salesInvoiceRepository.findByOrderId(orderId)).thenReturn(Optional.of(invoice));

        // Act
        salesOrderService.cancelOrder(orderId, request);

        // Assert
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(order.getNote()).contains("Khách đổi ý không lấy nữa");
        assertThat(invoice.getStatus()).isEqualTo("CANCELLED");

        verify(inventoryApi).releaseReservedStock(variantId, new BigDecimal("3"));
        verify(orderRepository).save(order);
        verify(salesInvoiceRepository).save(invoice);
    }

    @Test
    @DisplayName("Báo lỗi khi cố hủy đơn hàng đã hoàn tất (ORDER_CANNOT_BE_CANCELLED)")
    void cancelOrder_AlreadyCompleted_ThrowsAppException() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order completedOrder = Order.builder()
                .orderId(orderId)
                .orderStatus(OrderStatus.COMPLETED.name())
                .build();

        CancelOrderRequest request = CancelOrderRequest.builder()
                .cancelReason("Muốn hủy đơn đã nhận")
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(completedOrder));

        // Act & Assert
        assertThatThrownBy(() -> salesOrderService.cancelOrder(orderId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.ORDER_CANNOT_BE_CANCELLED);

        verify(inventoryApi, never()).releaseReservedStock(any(), any());
    }

    @Test
    @DisplayName("Chỉnh sửa thông tin đơn hàng trước khi xuất kho (pre-dispatch)")
    void updatePreDispatch_Success() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261031-0007")
                .customerId(customerId)
                .orderStatus(OrderStatus.PROCESSING.name())
                .deliveryAddress("Địa chỉ cũ")
                .note("Ghi chú cũ")
                .build();

        UpdatePreDispatchOrderRequest updateReq = UpdatePreDispatchOrderRequest.builder()
                .deliveryAddress("789 Cầu Giấy, Hà Nội")
                .note("Giao giờ hành chính")
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(financeApi.getTransactionsByOrderId(orderId)).thenReturn(List.of());

        // Act
        SalesOrderDetailResponse response = salesOrderService.updatePreDispatch(orderId, updateReq);

        // Assert
        assertThat(response).isNotNull();
        assertThat(order.getDeliveryAddress()).isEqualTo("789 Cầu Giấy, Hà Nội");
        assertThat(order.getNote()).isEqualTo("Giao giờ hành chính");
    }

    @Test
    @DisplayName("Tra cứu chi tiết đơn hàng kèm hóa đơn và lịch sử thanh toán")
    void getSalesOrderDetail_Success_IncludesInvoiceAndPayments() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261031-0008")
                .customerId(customerId)
                .orderStatus(OrderStatus.COMPLETED.name())
                .paymentStatus(PaymentStatus.PAID.name())
                .subtotal(new BigDecimal("1000000"))
                .totalAmount(new BigDecimal("1000000"))
                .build();

        SalesInvoice invoice = SalesInvoice.builder()
                .invoiceId(UUID.randomUUID())
                .invoiceCode("INV-20261031-0001")
                .orderId(orderId)
                .customerId(customerId)
                .totalAmount(new BigDecimal("1000000"))
                .status("PAID")
                .build();

        PaymentTransactionResponseDto tx = PaymentTransactionResponseDto.builder()
                .transactionId(UUID.randomUUID())
                .transactionCode("REC-20261031-0001")
                .orderId(orderId)
                .invoiceId(invoice.getInvoiceId())
                .amount(new BigDecimal("1000000"))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(financeApi.getTransactionsByOrderId(orderId)).thenReturn(List.of(tx));
        when(salesInvoiceRepository.findByOrderId(orderId)).thenReturn(Optional.of(invoice));

        // Act
        SalesOrderDetailResponse response = salesOrderService.getSalesOrderDetail(orderId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("SO-20261031-0008");
        assertThat(response.getInvoice()).isNotNull();
        assertThat(response.getInvoice().getInvoiceCode()).isEqualTo("INV-20261031-0001");
        assertThat(response.getPaymentTransactions()).hasSize(1);
        assertThat(response.getPaymentTransactions().get(0).invoiceId()).isEqualTo(invoice.getInvoiceId());
    }
}
