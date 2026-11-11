package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogBundleSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.finance.api.CreditControlApi;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationDecision;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.OrderPaymentMilestone;
import com.aquasuite.ims.module.sales.internal.entity.Quotation;
import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import com.aquasuite.ims.module.sales.internal.entity.QuotationPaymentMilestone;
import com.aquasuite.ims.module.sales.internal.enums.FulfillmentMethod;
import com.aquasuite.ims.module.sales.internal.enums.OrderChannel;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderType;
import com.aquasuite.ims.module.sales.internal.enums.PaymentMethod;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.enums.QuotationStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.mapper.SalesOrderMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.ConvertQuotationToOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateComboItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateOrderItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SalesOrderFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderSummaryResponse;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderPaymentMilestoneRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationPaymentMilestoneRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationRepository;
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

    @Mock private SalesInvoiceRepository salesInvoiceRepository;
    @Mock private SalesInvoiceItemRepository salesInvoiceItemRepository;
    @Mock private CreditControlApi creditControlApi;
    @Mock private OrderStockReservationService orderStockReservationService;
    @Mock private com.aquasuite.ims.module.finance.api.OrderReceivableApi orderReceivableApi;

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

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private QuotationItemRepository quotationItemRepository;

    @Mock
    private QuotationPaymentMilestoneRepository quotationPaymentMilestoneRepository;

    @Mock
    private OrderPaymentMilestoneRepository orderPaymentMilestoneRepository;

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
        org.mockito.Mockito.lenient().when(iamApi.getCurrentUserId()).thenReturn(staffId);
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "true,false", "false,true", "true,true"})
    @DisplayName("UC-21: Persist holding intent while preserving legacy stock/payment behavior")
    void createSalesOrder_Success_IndividualSku_ReservesStockAndSetsUnpaidProcessing(boolean deferred, boolean byPhone) {
        // Arrange
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(customerId)
                .orderType(OrderType.RETAIL)
                .orderChannel(OrderChannel.COUNTER)
                .fulfillmentMethod(FulfillmentMethod.IN_STORE_PICKUP)
                .paymentMethod(PaymentMethod.CASH)
                .discountAmount(new BigDecimal("500000"))
                .deliveryAddress("Nhận tại quầy Showroom")
                .note("Khách nhận tại quầy")
                .reserveStockAfterDeposit(deferred)
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .variantId(variantId)
                                .quantity(new BigDecimal("2"))
                                .discountAmount(BigDecimal.ZERO)
                                .build()
                ))
                .build();

        if (byPhone) {
            request.setCustomerId(null);
            request.setCustomerPhone(" 0988776655 ");
            request.setCustomerName("Counter customer");
            when(crmApi.getOrCreateCounterCustomer(" 0988776655 ", "Counter customer")).thenReturn(customerDto);
        } else {
            when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        }
        when(iamApi.getCurrentUserId()).thenReturn(staffId);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261103-0001");
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        if (!deferred) when(inventoryApi.checkAvailability(variantId, new BigDecimal("2"))).thenReturn(true);
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("SO-20261103-0001");
        assertThat(response.getOrderChannel()).isEqualTo("COUNTER");
        assertThat(response.getStoreInvoiceId()).isNotNull();
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING.name());
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID.name());
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("9500000"));
        assertThat(response.getItems()).hasSize(1);

        // Xác nhận chỉ đặt giữ tồn kho (reserved_qty), không tự ý trừ on_hand_qty (thuộc UC-37)
        verify(orderRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(order ->
                order.isReserveStockAfterDeposit() == deferred && customerId.equals(order.getCustomerId())));
        verify(crmApi, never()).getOrCreateWalkInCustomer();
        if (deferred) {
            verify(inventoryApi, never()).reserveStock(any(), any());
            verify(inventoryApi, never()).checkAvailability(any(), any());
        } else {
            verify(inventoryApi).reserveStock(variantId, new BigDecimal("2"));
        }
        verify(salesInvoiceRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(invoice ->
                "STORE".equals(invoice.getInvoiceType()) && "DRAFT".equals(invoice.getStatus())
                        && customerId.equals(invoice.getCustomerId())
                        && invoice.getTotalAmount().compareTo(new BigDecimal("9500000")) == 0));
        verify(salesInvoiceItemRepository).saveAll(anyList());
        verify(inventoryApi, never()).adjustPhysicalStock(any());
    }

    @Test
    void legacyPosChannelIsStoredAsCounter() {
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(customerId).orderChannel(OrderChannel.POS).paymentMethod(PaymentMethod.CASH)
                .items(List.of(CreateOrderItemRequest.builder().variantId(variantId)
                        .quantity(BigDecimal.ONE).discountAmount(BigDecimal.ZERO).build())).build();
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(iamApi.getCurrentUserId()).thenReturn(staffId);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261103-0100");
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        when(inventoryApi.checkAvailability(variantId, BigDecimal.ONE)).thenReturn(true);
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(salesOrderService.createSalesOrder(request).getOrderChannel()).isEqualTo("COUNTER");
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
                .packagePrice(new BigDecimal("14000000"))
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
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("SO-20261103-0002");
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING.name());
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID.name());
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("28000000"));
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getDisplayItems()).hasSize(1);
        assertThat(response.getDisplayItems().getFirst().quantity()).isEqualByComparingTo("2");
        assertThat(response.getDisplayItems().getFirst().components()).hasSize(2);
        assertThat(response.getDisplayItems().getFirst().unitPrice()).isEqualByComparingTo("14000000");
        assertThat(response.getDisplayItems().getFirst().lineTotal()).isEqualByComparingTo("28000000");
        assertThat(response.getItems().stream().map(i -> i.getLineTotal())
                .reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("28000000");
        assertThat(response.getItems().stream().map(i -> i.getDiscountAmount())
                .reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("2000000");
        verify(salesInvoiceRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(invoice ->
                invoice.getTotalAmount().compareTo(new BigDecimal("28000000")) == 0));


        verify(inventoryApi).reserveStock(compVariant1, new BigDecimal("2"));
        verify(inventoryApi).reserveStock(compVariant2, new BigDecimal("2"));
    }

    @Test
    @DisplayName("UC-21: Tạo đơn CREDIT hợp lệ sau kiểm tra UC-24, chưa tính hạn theo hóa đơn điện tử")
    void createSalesOrder_Success_CreditEligible_AuthorizesAndLeavesDueDateForInvoice() {
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
        when(creditControlApi.authorize(any())).thenReturn(new CreditAuthorizationDecision(true, "APPROVED", new BigDecimal("100000000")));
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261103-0003");
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        when(inventoryApi.checkAvailability(variantId, BigDecimal.ONE)).thenReturn(true);
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);

        // Assert
        assertThat(response.getDueDate()).isNull();
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID.name());
        verify(creditControlApi).authorize(any());
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
    @DisplayName("UC-21: Chặn đơn CREDIT khi UC-24 từ chối")
    void createSalesOrder_CreditDenied_ThrowsAppException() {
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
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261103-0004");
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        when(inventoryApi.checkAvailability(variantId, BigDecimal.ONE)).thenReturn(true);
        when(creditControlApi.authorize(any())).thenReturn(new CreditAuthorizationDecision(false, "OVERDUE", BigDecimal.ZERO));

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
                .orderChannel(OrderChannel.COUNTER.name())
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
                .orderChannel(OrderChannel.COUNTER.name())
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

    // =========================================================================
    // UC-28 / UC-073: Chuyển đổi báo giá thành đơn hàng công trình (Convert Quotation to Order)
    // =========================================================================

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("UC-28: Persist both explicit and default holding intent when converting")
    void convertQuotationToOrder_Success_WhenAccepted(boolean deferred) {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .quotationCode("BG-202611-0001")
                .revisionNo(1)
                .customerId(customerId)
                .projectName("Villa Ecopark")
                .status(QuotationStatus.ACCEPTED)
                .deliveryAddress("Ecopark, Hưng Yên")
                .deliverySchedule("Giao làm 2 đợt theo tiến độ")
                .vatRate(new BigDecimal("10.00"))
                .shippingFee(new BigDecimal("500000"))
                .depositPercent(new BigDecimal("30.00"))
                .depositAmount(new BigDecimal("2700000"))
                .creditTermDays(30)
                .remainingDebtAmount(new BigDecimal("6300000"))
                .debtCondition("Thanh toán trong 30 ngày sau nghiệm thu")
                .note("Giao hàng giờ hành chính")
                .totalAmount(new BigDecimal("9000000"))
                .createdBy(staffId)
                .build();

        QuotationItem qItem = QuotationItem.builder()
                .quotationItemId(UUID.randomUUID())
                .quotationId(quotationId)
                .variantId(variantId)
                .sku("SKU-TOILET-01")
                .unit("bộ")
                .quantity(new BigDecimal("2"))
                .unitPrice(new BigDecimal("5000000"))
                .discountRate(new BigDecimal("10")) // 10% chiết khấu -> 1,000,000 đ
                .build();

        QuotationPaymentMilestone m1 = QuotationPaymentMilestone.builder()
                .milestoneId(UUID.randomUUID())
                .quotationId(quotationId)
                .stageName("Đợt 1: Đặt cọc 30%")
                .percent(new BigDecimal("30.00"))
                .sortOrder(1)
                .build();
        QuotationPaymentMilestone m2 = QuotationPaymentMilestone.builder()
                .milestoneId(UUID.randomUUID())
                .quotationId(quotationId)
                .stageName("Đợt 2: Quyết toán 70%")
                .percent(new BigDecimal("70.00"))
                .sortOrder(2)
                .build();

        ConvertQuotationToOrderRequest request = ConvertQuotationToOrderRequest.builder()
                .note("Đã thỏa thuận cọc 30%")
                .reserveStockAfterDeposit(deferred)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(orderRepository.findByQuotationId(quotationId)).thenReturn(Optional.empty());
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId)).thenReturn(List.of(qItem));
        when(quotationPaymentMilestoneRepository.findByQuotationIdOrderBySortOrderAsc(quotationId))
                .thenReturn(List.of(m1, m2));
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261105-0001");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(orderPaymentMilestoneRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SalesOrderDetailResponse response = salesOrderService.convertQuotationToOrder(quotationId, request);

        verify(orderRepository).save(org.mockito.ArgumentMatchers.argThat(order ->
                order.isReserveStockAfterDeposit()));
        verify(inventoryApi, never()).reserveStock(any(), any());
        verify(orderStockReservationService).registerConvertedOrder(any(Order.class), anyList(), eq(staffId));

        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("SO-20261105-0001");
        assertThat(response.getQuotationId()).isEqualTo(quotationId);
        assertThat(response.getQuotationCode()).isEqualTo("BG-202611-0001");
        assertThat(response.getProjectName()).isEqualTo("Villa Ecopark");
        assertThat(response.getDeliverySchedule()).isEqualTo("Giao làm 2 đợt theo tiến độ");
        assertThat(response.getVatRate()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(response.getShippingFee()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(response.getDepositPercent()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(response.getDepositAmount()).isEqualByComparingTo(new BigDecimal("2700000"));
        assertThat(response.getCreditTermDays()).isEqualTo(30);
        assertThat(response.getRemainingDebtAmount()).isEqualByComparingTo(new BigDecimal("6300000"));
        assertThat(response.getDebtCondition()).isEqualTo("Thanh toán trong 30 ngày sau nghiệm thu");
        assertThat(response.getOrderType()).isEqualTo(OrderType.PROJECT.name());
        assertThat(response.getOrderChannel()).isEqualTo(OrderChannel.DIRECT_SALES.name());
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING.name());
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID.name());
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("9000000"));
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getMilestones()).hasSize(2);
        assertThat(response.getMilestones().get(0).getStageName()).isEqualTo("Đợt 1: Đặt cọc 30%");
        assertThat(response.getMilestones().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("2700000.00"));
        assertThat(response.getMilestones().get(1).getStageName()).isEqualTo("Đợt 2: Quyết toán 70%");
        assertThat(response.getMilestones().get(1).getAmount()).isEqualByComparingTo(new BigDecimal("6300000.00"));
        assertThat(response.getNote()).contains("Villa Ecopark").contains("Đã thỏa thuận cọc 30%");
    }

    @Test
    @DisplayName("UC-28 / UC-073: Chuyển đổi báo giá đã duyệt (APPROVED) -> Tự động chuyển báo giá sang ACCEPTED")
    void convertQuotationToOrder_Success_WhenApproved_UpdatesQuotationToAccepted() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .quotationCode("BG-202611-0002")
                .revisionNo(1)
                .customerId(customerId)
                .status(QuotationStatus.APPROVED)
                .totalAmount(new BigDecimal("5000000"))
                .createdBy(staffId)
                .build();

        QuotationItem qItem = QuotationItem.builder()
                .quotationItemId(UUID.randomUUID())
                .quotationId(quotationId)
                .variantId(variantId)
                .sku("SKU-TOILET-01")
                .unit("cái")
                .quantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal("5000000"))
                .discountRate(BigDecimal.ZERO)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(orderRepository.findByQuotationId(quotationId)).thenReturn(Optional.empty());
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId)).thenReturn(List.of(qItem));
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        when(orderCodeGenerator.generateOrderCode()).thenReturn("SO-20261105-0002");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SalesOrderDetailResponse response = salesOrderService.convertQuotationToOrder(quotationId, null);

        assertThat(response).isNotNull();
        assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.ACCEPTED);
        assertThat(quotation.getAcceptedAt()).isNotNull();
        verify(quotationRepository).save(quotation);
        verify(orderRepository).save(org.mockito.ArgumentMatchers.argThat(order -> !order.isReserveStockAfterDeposit()));
        verify(orderStockReservationService, never()).registerConvertedOrder(any(),anyList(),any());
    }

    @Test
    @DisplayName("UC-28 / UC-073: Báo lỗi khi không tìm thấy báo giá (QUOTATION_NOT_FOUND)")
    void convertQuotationToOrder_Throws_WhenQuotationNotFound() {
        UUID quotationId = UUID.randomUUID();
        when(quotationRepository.findById(quotationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salesOrderService.convertQuotationToOrder(quotationId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.QUOTATION_NOT_FOUND);
    }

    @Test
    @DisplayName("UC-28 / UC-073: Báo lỗi khi báo giá đang ở trạng thái DRAFT (QUOTATION_NOT_ACCEPTED_OR_APPROVED)")
    void convertQuotationToOrder_Throws_WhenStatusInvalid() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.DRAFT)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));

        assertThatThrownBy(() -> salesOrderService.convertQuotationToOrder(quotationId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.QUOTATION_NOT_ACCEPTED_OR_APPROVED);
    }

    @Test
    @DisplayName("UC-28 / UC-073: Chặn chuyển đổi trùng lặp nếu báo giá đã có đơn hàng (ALREADY_CONVERTED_TO_ORDER)")
    void convertQuotationToOrder_Throws_WhenAlreadyConverted() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .quotationCode("BG-202611-0003")
                .status(QuotationStatus.ACCEPTED)
                .build();

        Order existingOrder = Order.builder()
                .orderId(UUID.randomUUID())
                .orderCode("SO-20261103-9999")
                .quotationId(quotationId)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(orderRepository.findByQuotationId(quotationId)).thenReturn(Optional.of(existingOrder));

        assertThatThrownBy(() -> salesOrderService.convertQuotationToOrder(quotationId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.ALREADY_CONVERTED_TO_ORDER);
    }

    @Test
    @DisplayName("UC-28 / UC-073: Báo lỗi khi danh sách sản phẩm trong báo giá trống (ORDER_ITEMS_EMPTY)")
    void convertQuotationToOrder_Throws_WhenNoItems() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.ACCEPTED)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(orderRepository.findByQuotationId(quotationId)).thenReturn(Optional.empty());
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId)).thenReturn(List.of());

        assertThatThrownBy(() -> salesOrderService.convertQuotationToOrder(quotationId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.ORDER_ITEMS_EMPTY);
    }

    @Test
    @DisplayName("UC-28 / UC-073: Báo lỗi khi biến thể sản phẩm trong báo giá đã ngừng hoạt động (SKU_NOT_ACTIVE)")
    void convertQuotationToOrder_Throws_WhenSkuInactive() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .customerId(customerId)
                .status(QuotationStatus.ACCEPTED)
                .build();

        QuotationItem qItem = QuotationItem.builder()
                .quotationItemId(UUID.randomUUID())
                .quotationId(quotationId)
                .variantId(variantId)
                .quantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal("1000000"))
                .build();

        CatalogSkuSummaryDto inactiveSku = CatalogSkuSummaryDto.builder()
                .variantId(variantId)
                .sku("SKU-INACTIVE")
                .status("INACTIVE")
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(orderRepository.findByQuotationId(quotationId)).thenReturn(Optional.empty());
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId)).thenReturn(List.of(qItem));
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(inactiveSku));

        assertThatThrownBy(() -> salesOrderService.convertQuotationToOrder(quotationId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.SKU_NOT_ACTIVE);
    }
}
