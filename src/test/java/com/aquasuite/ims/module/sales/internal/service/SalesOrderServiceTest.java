package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogBundleSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.enums.FulfillmentMethod;
import com.aquasuite.ims.module.sales.internal.enums.OrderChannel;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderType;
import com.aquasuite.ims.module.sales.internal.enums.PaymentMethod;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.mapper.SalesOrderMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateComboItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateOrderItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SalesOrderFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderSummaryResponse;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CatalogApi catalogApi;

    @Mock
    private InventoryApi inventoryApi;

    @Mock
    private CrmApi crmApi;

    @Mock
    private IamApi iamApi;

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
                .sku("SKU-TOILET-01")
                .productName("Bồn cầu thông minh TOTO Neorest")
                .variantName("Trắng sứ - xả tự động")
                .sellingPrice(new BigDecimal("5000000"))
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("UC-21: Tạo đơn hàng bán lẻ thành công -> Đặt giữ tồn kho (reserveStock), orderStatus=PROCESSING, paymentStatus=UNPAID")
    void createSalesOrder_Success_IndividualSku_ReservesStockAndSetsUnpaidProcessing() {
        // Arrange
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(customerId)
                .orderType(OrderType.RETAIL)
                .orderChannel(OrderChannel.IN_STORE)
                .fulfillmentMethod(FulfillmentMethod.IN_STORE_PICKUP)
                .paymentMethod(PaymentMethod.CASH)
                .discountAmount(new BigDecimal("500000"))
                .deliveryAddress("Nhận tại quầy Showroom")
                .note("Khách nhận tại quầy")
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
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261103-0001");
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        when(inventoryApi.checkAvailability(variantId, new BigDecimal("2"))).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("SO-20261103-0001");
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING.name());
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID.name());
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("9500000"));
        assertThat(response.getItems()).hasSize(1);

        // Xác nhận chỉ đặt giữ tồn kho (reserved_qty), không tự ý trừ on_hand_qty (thuộc UC-37)
        verify(inventoryApi).reserveStock(variantId, new BigDecimal("2"));
        verify(inventoryApi, never()).adjustPhysicalStock(any());
    }

    @Test
    @DisplayName("UC-21: Tạo đơn hàng với gói Combo thành công -> Kiểm tra và đặt giữ tồn kho cho từng SKU thành phần")
    void createSalesOrder_Success_WithComboPackage() {
        // Arrange
        UUID comboId = UUID.randomUUID();
        UUID compVariant1 = UUID.randomUUID();
        UUID compVariant2 = UUID.randomUUID();

        CatalogBundleSummaryDto comboDto = CatalogBundleSummaryDto.builder()
                .bundleId(comboId)
                .bundleName("Combo Phòng Tắm Master")
                .packagePrice(new BigDecimal("15000000"))
                .status("ACTIVE")
                .items(List.of(
                        CatalogBundleSummaryDto.CatalogBundleItemDto.builder()
                                .variantId(compVariant1)
                                .sku("SKU-LAVABO-01")
                                .variantName("Chậu rửa đặt bàn")
                                .quantity(1)
                                .sellingPrice(new BigDecimal("5000000"))
                                .build(),
                        CatalogBundleSummaryDto.CatalogBundleItemDto.builder()
                                .variantId(compVariant2)
                                .sku("SKU-SHOWER-01")
                                .variantName("Sen tắm nhiệt độ")
                                .quantity(1)
                                .sellingPrice(new BigDecimal("10000000"))
                                .build()
                ))
                .build();

        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(customerId)
                .fulfillmentMethod(FulfillmentMethod.SCHEDULED_DELIVERY)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .combos(List.of(
                        CreateComboItemRequest.builder()
                                .comboId(comboId)
                                .quantity(2)
                                .build()
                ))
                .build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(iamApi.getCurrentUserId()).thenReturn(staffId);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261103-0002");
        when(catalogApi.getBundleSummary(comboId)).thenReturn(Optional.of(comboDto));
        when(inventoryApi.checkAvailability(compVariant1, new BigDecimal("2"))).thenReturn(true);
        when(inventoryApi.checkAvailability(compVariant2, new BigDecimal("2"))).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("SO-20261103-0002");
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING.name());
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID.name());
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("30000000"));
        assertThat(response.getItems()).hasSize(2);

        verify(inventoryApi).reserveStock(compVariant1, new BigDecimal("2"));
        verify(inventoryApi).reserveStock(compVariant2, new BigDecimal("2"));
    }

    @Test
    @DisplayName("UC-21: Tạo đơn hàng mua công nợ (CREDIT) hợp lệ -> Tự động gán dueDate = +30 ngày")
    void createSalesOrder_Success_CreditEligible_SetsDueDate() {
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
        when(orderRepository.hasOverdueUnpaidOrders(eq(customerId), any(LocalDate.class))).thenReturn(false);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261103-0003");
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        when(inventoryApi.checkAvailability(variantId, BigDecimal.ONE)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);

        // Assert
        assertThat(response.getDueDate()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID.name());
    }

    @Test
    @DisplayName("UC-21: Báo lỗi khi đơn hàng không có sản phẩm hay combo nào (ORDER_ITEMS_EMPTY)")
    void createSalesOrder_EmptyItems_ThrowsAppException() {
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(customerId)
                .items(List.of())
                .combos(List.of())
                .build();

        assertThatThrownBy(() -> salesOrderService.createSalesOrder(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.ORDER_ITEMS_EMPTY);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("UC-21: Báo lỗi khi số lượng tồn kho khả dụng không đủ (INSUFFICIENT_STOCK)")
    void createSalesOrder_InsufficientStock_ThrowsAppException() {
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

        assertThatThrownBy(() -> salesOrderService.createSalesOrder(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.INSUFFICIENT_STOCK);

        verify(inventoryApi, never()).reserveStock(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("UC-21: Chặn mua hàng công nợ nếu có đơn chưa thanh toán quá hạn trên 60 ngày (CREDIT_LIMIT_EXCEEDED / MSG63)")
    void createSalesOrder_CreditOverdue_ThrowsAppException() {
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

        assertThatThrownBy(() -> salesOrderService.createSalesOrder(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.CREDIT_LIMIT_EXCEEDED);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("UC-21: Lấy danh sách đơn hàng có phân trang và bộ lọc thành công")
    @SuppressWarnings("unchecked")
    void getSalesOrders_Success() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261103-0008")
                .customerId(customerId)
                .salesStaffId(staffId)
                .orderType(OrderType.RETAIL.name())
                .orderChannel(OrderChannel.IN_STORE.name())
                .orderStatus(OrderStatus.PROCESSING.name())
                .paymentStatus(PaymentStatus.UNPAID.name())
                .totalAmount(new BigDecimal("5000000"))
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(new OrderItem()));
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));

        SalesOrderFilterRequest filter = SalesOrderFilterRequest.builder()
                .keyword("SO-20261103")
                .orderStatus(OrderStatus.PROCESSING)
                .build();

        Page<SalesOrderSummaryResponse> result = salesOrderService.getSalesOrders(filter, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getOrderCode()).isEqualTo("SO-20261103-0008");
        assertThat(result.getContent().get(0).getItemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("UC-21: Tra cứu chi tiết đơn hàng thành công")
    void getSalesOrderDetail_Success() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261103-0009")
                .customerId(customerId)
                .orderType(OrderType.RETAIL.name())
                .orderChannel(OrderChannel.IN_STORE.name())
                .orderStatus(OrderStatus.PROCESSING.name())
                .paymentStatus(PaymentStatus.UNPAID.name())
                .orderDate(OffsetDateTime.now())
                .subtotal(new BigDecimal("5000000"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("5000000"))
                .build();

        OrderItem item = OrderItem.builder()
                .orderItemId(UUID.randomUUID())
                .orderId(orderId)
                .variantId(variantId)
                .sku("SKU-TOILET-01")
                .productName("Bồn cầu thông minh TOTO Neorest")
                .variantName("Trắng sứ")
                .quantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal("5000000"))
                .discountAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("5000000"))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));

        SalesOrderDetailResponse response = salesOrderService.getSalesOrderDetail(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("SO-20261103-0009");
        assertThat(response.getCustomerName()).isEqualTo("Khách Hàng Thử Nghiệm");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getSku()).isEqualTo("SKU-TOILET-01");
    }
}
