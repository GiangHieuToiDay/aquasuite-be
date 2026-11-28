package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.service.sales.impl.OrderReservationServiceImpl;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogBundleSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.service.finance.CreditControlService;
import com.aquasuite.ims.dto.finance.response.CreditAuthorizationDecision;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.entity.sales.OnlinePayosPayment;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.entity.sales.OrderPaymentMilestone;
import com.aquasuite.ims.entity.sales.Quotation;
import com.aquasuite.ims.entity.sales.QuotationItem;
import com.aquasuite.ims.entity.sales.QuotationPaymentMilestone;
import com.aquasuite.ims.entity.sales.SalesInvoice;
import com.aquasuite.ims.enums.sales.FulfillmentMethod;
import com.aquasuite.ims.enums.sales.OrderChannel;
import com.aquasuite.ims.enums.sales.OrderStatus;
import com.aquasuite.ims.enums.sales.OrderType;
import com.aquasuite.ims.enums.sales.PaymentMethod;
import com.aquasuite.ims.enums.sales.PaymentStatus;
import com.aquasuite.ims.enums.sales.QuotationStatus;
import com.aquasuite.ims.exception.sales.SalesErrorCode;
import com.aquasuite.ims.integration.sales.PayosClient;
import com.aquasuite.ims.mapper.sales.SalesOrderMapper;
import com.aquasuite.ims.dto.sales.request.ConfirmOnlineOrderRequest;
import com.aquasuite.ims.dto.sales.request.ConvertQuotationToOrderRequest;
import com.aquasuite.ims.dto.sales.request.CreateComboItemRequest;
import com.aquasuite.ims.dto.sales.request.CreateOrderItemRequest;
import com.aquasuite.ims.dto.sales.request.CreateSalesOrderRequest;
import com.aquasuite.ims.dto.sales.request.RejectOnlineOrderRequest;
import com.aquasuite.ims.dto.sales.request.SalesOrderFilterRequest;
import com.aquasuite.ims.dto.sales.request.UpdateOrderFulfillmentRequest;
import com.aquasuite.ims.dto.sales.response.SalesOrderDetailResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderSummaryResponse;
import com.aquasuite.ims.repository.sales.OnlinePayosPaymentRepository;
import com.aquasuite.ims.repository.sales.OrderItemRepository;
import com.aquasuite.ims.repository.sales.OrderPaymentMilestoneRepository;
import com.aquasuite.ims.repository.sales.OrderRepository;
import com.aquasuite.ims.repository.sales.QuotationItemRepository;
import com.aquasuite.ims.repository.sales.QuotationPaymentMilestoneRepository;
import com.aquasuite.ims.repository.sales.QuotationRepository;
import com.aquasuite.ims.repository.sales.SalesInvoiceItemRepository;
import com.aquasuite.ims.repository.sales.SalesInvoiceRepository;
import com.aquasuite.ims.service.sales.helper.OrderCodeGenerator;
import com.aquasuite.ims.service.sales.impl.SalesOrderServiceImpl;
import com.aquasuite.ims.exception.AppException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
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
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    @Mock private CreditControlService creditControlApi;
    @Mock private OrderReservationServiceImpl orderStockReservationService;
    @Mock private com.aquasuite.ims.service.finance.PaymentService orderReceivableApi;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private CatalogLookupService catalogApi;

    @Mock
    private InventoryService inventoryApi;

    @Mock
    private CustomerService crmApi;

    @Mock
    private IamQueryService iamApi;

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

    @Mock
    private OnlinePayosPaymentRepository onlinePayosPaymentRepository;

    @Mock
    private PayosClient payosClient;

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

        org.mockito.Mockito.lenient().when(creditControlApi.authorize(any()))
                .thenReturn(new CreditAuthorizationDecision(true, null, new BigDecimal("100000000")));
        org.mockito.Mockito.lenient().when(crmApi.getCustomerByPhone(any())).thenReturn(Optional.of(customerDto));
        org.mockito.Mockito.lenient().when(crmApi.getOrCreateCounterCustomer(any(), any())).thenReturn(customerDto);
        org.mockito.Mockito.lenient().when(crmApi.getOrCreateWalkInCustomer(any(), any())).thenReturn(customerDto);
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
            org.mockito.Mockito.lenient().when(crmApi.getOrCreateCounterCustomer(" 0988776655 ", "Counter customer")).thenReturn(customerDto);
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

    @Test
    @DisplayName("UC-21.2: Cập nhật thông tin giao nhận thành công khi đơn đang PROCESSING")
    void updateFulfillmentInfo_Success_WhenProcessing() {
        UUID orderId = UUID.randomUUID();
        Order existingOrder = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261111-0001")
                .customerId(customerId)
                .orderStatus(OrderStatus.PROCESSING.name())
                .paymentStatus(PaymentStatus.UNPAID.name())
                .deliveryAddress("Địa chỉ cũ")
                .deliverySchedule("Lịch cũ")
                .note("Ghi chú cũ")
                .build();

        UpdateOrderFulfillmentRequest updateReq = UpdateOrderFulfillmentRequest.builder()
                .deliveryAddress("123 Phố Mới, Thuận Thành, Bắc Ninh")
                .deliverySchedule("Giao sáng Chủ Nhật trước 10h")
                .note("Gọi trước 15 phút khi đến")
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));

        SalesOrderDetailResponse response = salesOrderService.updateFulfillmentInfo(orderId, updateReq);

        assertThat(response).isNotNull();
        assertThat(existingOrder.getDeliveryAddress()).isEqualTo("123 Phố Mới, Thuận Thành, Bắc Ninh");
        assertThat(existingOrder.getDeliverySchedule()).isEqualTo("Giao sáng Chủ Nhật trước 10h");
        assertThat(existingOrder.getNote()).isEqualTo("Gọi trước 15 phút khi đến");
        verify(orderRepository).save(existingOrder);
    }

    @Test
    @DisplayName("UC-21.2: Báo lỗi khi cập nhật thông tin giao nhận của đơn đã COMPLETED")
    void updateFulfillmentInfo_Throws_WhenNotProcessing() {
        UUID orderId = UUID.randomUUID();
        Order completedOrder = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261111-0002")
                .orderStatus(OrderStatus.COMPLETED.name())
                .build();

        UpdateOrderFulfillmentRequest updateReq = UpdateOrderFulfillmentRequest.builder()
                .deliveryAddress("Địa chỉ mới")
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(completedOrder));

        assertThatThrownBy(() -> salesOrderService.updateFulfillmentInfo(orderId, updateReq))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.ORDER_NOT_MODIFIABLE);
    }

    @Test
    @DisplayName("UC-21.2: Xác nhận hoàn tất đơn hàng thành công khi đơn đang PROCESSING và đã thanh toán đủ (PAID)")
    void completeSalesOrder_Success_WhenProcessingAndPaid() {
        UUID orderId = UUID.randomUUID();
        Order processingPaidOrder = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261111-0003")
                .customerId(customerId)
                .orderStatus(OrderStatus.PROCESSING.name())
                .paymentStatus(PaymentStatus.PAID.name())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(processingPaidOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));

        SalesOrderDetailResponse response = salesOrderService.completeSalesOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(processingPaidOrder.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED.name());
        verify(orderRepository).save(processingPaidOrder);
    }

    @Test
    @DisplayName("UC-21.2: Báo lỗi khi hoàn tất đơn hàng mà chưa thanh toán đủ (UNPAID hoặc PARTIALLY_PAID)")
    void completeSalesOrder_Throws_WhenNotPaid() {
        UUID orderId = UUID.randomUUID();
        Order unpaidOrder = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261111-0004")
                .orderStatus(OrderStatus.PROCESSING.name())
                .paymentStatus(PaymentStatus.UNPAID.name())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(unpaidOrder));

        assertThatThrownBy(() -> salesOrderService.completeSalesOrder(orderId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", SalesErrorCode.ORDER_CANNOT_COMPLETE);
    }

    @Test
    @DisplayName("UC-21 / Search: Batch load khách hàng theo customerIds thay cho N+1 query")
    @SuppressWarnings("unchecked")
    void getSalesOrders_batchLoadsCustomersAndSkipsPerRowLookup() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261123-0001")
                .customerId(customerId)
                .orderType(OrderType.RETAIL.name())
                .orderChannel(OrderChannel.ONLINE.name())
                .orderStatus(OrderStatus.PENDING_CONFIRMATION.name())
                .paymentStatus(PaymentStatus.PAID.name())
                .totalAmount(new BigDecimal("4000"))
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(crmApi.getCustomersByIds(any())).thenReturn(Map.of(customerId, customerDto));

        Page<SalesOrderSummaryResponse> result = salesOrderService.getSalesOrders(
                SalesOrderFilterRequest.builder().build(),
                pageable
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCustomerPhone()).isEqualTo("0988776655");
        assertThat(result.getContent().get(0).getOrderStatus()).isEqualTo("PENDING_CONFIRMATION");
        verify(crmApi).getCustomersByIds(any());
        verify(crmApi, never()).getCustomerById(any());
    }

    @Test
    @DisplayName("UC-21 / Search: Specification tìm gần đúng SĐT (kể cả có khoảng trắng), tên KH và deliveryAddress")
    @SuppressWarnings("unchecked")
    void getSalesOrders_keywordSpecificationMatchesPartialAndCompactPhoneAndDeliveryAddress() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(Page.empty(pageable));
        when(crmApi.findCustomerIdsByKeyword("0987 654")).thenReturn(List.of());
        when(crmApi.findCustomerIdsByKeyword("0987654")).thenReturn(List.of(customerId));

        SalesOrderFilterRequest filter = SalesOrderFilterRequest.builder()
                .keyword("0987 654")
                .build();

        salesOrderService.getSalesOrders(filter, pageable);

        ArgumentCaptor<Specification<Order>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(orderRepository).findAll(specCaptor.capture(), eq(pageable));

        Root<Order> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> orderCodePath = mock(Path.class);
        Path<Object> projectNamePath = mock(Path.class);
        Path<Object> deliveryAddressPath = mock(Path.class);
        Path<Object> customerIdPath = mock(Path.class);
        Expression<String> lowerOrderCode = mock(Expression.class);
        Expression<String> lowerProjectName = mock(Expression.class);
        Expression<String> lowerDeliveryAddress = mock(Expression.class);

        when(root.get("orderCode")).thenReturn(orderCodePath);
        when(root.get("projectName")).thenReturn(projectNamePath);
        when(root.get("deliveryAddress")).thenReturn(deliveryAddressPath);
        when(root.get("customerId")).thenReturn(customerIdPath);
        when(cb.lower(any())).thenReturn(lowerOrderCode, lowerProjectName, lowerDeliveryAddress, lowerDeliveryAddress);
        when(cb.like(any(Expression.class), anyString(), eq('!'))).thenReturn(mock(Predicate.class));
        when(customerIdPath.in(any(Collection.class))).thenReturn(mock(Predicate.class));
        when(cb.or(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        specCaptor.getValue().toPredicate(root, query, cb);

        verify(crmApi).findCustomerIdsByKeyword("0987 654");
        verify(crmApi).findCustomerIdsByKeyword("0987654");
        verify(cb).like(lowerDeliveryAddress, "%0987 654%", '!');
        verify(cb).like(lowerDeliveryAddress, "%0987654%", '!');
        verify(customerIdPath).in(eq(java.util.Set.of(customerId)));
    }

    @Test
    @DisplayName("UC-21 / Search: Lọc orderChannel=COUNTER bao gồm COUNTER & POS, PENDING_CONFIRMATION và múi giờ +07:00")
    @SuppressWarnings("unchecked")
    void getSalesOrders_filterByCounterChannelIncludesCounterAndPos_andVietnamTimezone() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        SalesOrderFilterRequest filter = SalesOrderFilterRequest.builder()
                .orderChannel(OrderChannel.COUNTER)
                .orderStatus(OrderStatus.PENDING_CONFIRMATION)
                .fromDate(LocalDate.of(2026, 11, 18))
                .build();

        salesOrderService.getSalesOrders(filter, pageable);

        ArgumentCaptor<Specification<Order>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(orderRepository).findAll(specCaptor.capture(), eq(pageable));

        Root<Order> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> orderStatusPath = mock(Path.class);
        Path<Object> orderChannelPath = mock(Path.class);
        Path<Object> orderDatePath = mock(Path.class);

        when(root.get("orderStatus")).thenReturn(orderStatusPath);
        when(root.get("orderChannel")).thenReturn(orderChannelPath);
        when(root.get("orderDate")).thenReturn(orderDatePath);
        when(cb.equal(orderStatusPath, "PENDING_CONFIRMATION")).thenReturn(mock(Predicate.class));
        when(orderChannelPath.in("COUNTER", "POS")).thenReturn(mock(Predicate.class));
        when(cb.greaterThanOrEqualTo(any(Expression.class), any(OffsetDateTime.class))).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        specCaptor.getValue().toPredicate(root, query, cb);

        verify(cb).equal(orderStatusPath, "PENDING_CONFIRMATION");
        verify(orderChannelPath).in("COUNTER", "POS");
        OffsetDateTime expectedFrom = OffsetDateTime.of(2026, 11, 18, 0, 0, 0, 0, ZoneOffset.ofHours(7));
        verify(cb).greaterThanOrEqualTo(eq((Expression) orderDatePath), eq(expectedFrom));
    }

    @Test
    @DisplayName("UC-22: Xác nhận đơn hàng Online COD (PENDING_CONFIRMATION -> PROCESSING), giữ tồn kho và tạo hóa đơn STORE DRAFT")
    void confirmOnlineOrder_CodOrder_ReservesStockAndCreatesStoreDraftInvoice() {
        UUID orderId = UUID.randomUUID();
        Order onlineOrder = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261125-0001")
                .customerId(customerId)
                .orderType(OrderType.RETAIL.name())
                .orderChannel(OrderChannel.ONLINE.name())
                .orderStatus(OrderStatus.PENDING_CONFIRMATION.name())
                .paymentStatus(PaymentStatus.UNPAID.name())
                .paymentMethod(PaymentMethod.CASH.name())
                .subtotal(new BigDecimal("10000000"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("10000000"))
                .deliveryAddress("123 Lê Lợi, Q.1, TP.HCM")
                .build();

        OrderItem orderItem = OrderItem.builder()
                .orderItemId(UUID.randomUUID())
                .orderId(orderId)
                .variantId(variantId)
                .sku(skuDto.sku())
                .productName(skuDto.productName())
                .variantName(skuDto.variantName())
                .quantity(new BigDecimal("2"))
                .unitPrice(new BigDecimal("5000000"))
                .discountAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("10000000"))
                .build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(onlineOrder));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(onlineOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
        when(catalogApi.getSkuSummary(variantId)).thenReturn(Optional.of(skuDto));
        when(inventoryApi.checkAvailability(variantId, new BigDecimal("2"))).thenReturn(true);
        when(salesInvoiceRepository.findByOrderIdAndInvoiceType(orderId, "STORE")).thenReturn(Optional.empty());
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));

        ConfirmOnlineOrderRequest request = ConfirmOnlineOrderRequest.builder()
                .deliveryAddress("123 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM")
                .deliverySchedule("Giao sáng 26/11")
                .note("Đã gọi chốt với khách")
                .build();

        SalesOrderDetailResponse response = salesOrderService.confirmOnlineOrder(orderId, request);

        assertThat(response.getOrderStatus()).isEqualTo("PROCESSING");
        assertThat(response.getDeliverySchedule()).isEqualTo("Giao sáng 26/11");
        assertThat(onlineOrder.getSalesStaffId()).isEqualTo(staffId);
        verify(inventoryApi).reserveStock(variantId, new BigDecimal("2"));
        ArgumentCaptor<SalesInvoice> invoiceCaptor = ArgumentCaptor.forClass(SalesInvoice.class);
        verify(salesInvoiceRepository).saveAndFlush(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue().getInvoiceType()).isEqualTo("STORE");
        assertThat(invoiceCaptor.getValue().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("UC-22: Chặn xác nhận đơn Online chọn BANK_TRANSFER (PayOS) khi chưa thanh toán")
    void confirmOnlineOrder_PayosUnpaid_ThrowsOnlineOrderPayosUnpaid() {
        UUID orderId = UUID.randomUUID();
        Order onlineOrder = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261125-0002")
                .customerId(customerId)
                .orderType(OrderType.RETAIL.name())
                .orderChannel(OrderChannel.ONLINE.name())
                .orderStatus(OrderStatus.PENDING_CONFIRMATION.name())
                .paymentStatus(PaymentStatus.UNPAID.name())
                .paymentMethod(PaymentMethod.BANK_TRANSFER.name())
                .totalAmount(new BigDecimal("5000000"))
                .build();

        OnlinePayosPayment payosPayment = OnlinePayosPayment.builder()
                .payosPaymentId(UUID.randomUUID())
                .orderId(orderId)
                .payosOrderCode(12345678L)
                .status("PENDING")
                .amount(5000000L)
                .build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(onlineOrder));
        when(onlinePayosPaymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payosPayment));
        when(payosClient.getPaymentLinkInfo(12345678L))
                .thenReturn(new PayosClient.PaymentLinkStatusResult(12345678L, "link-1", 5000000L, 0L, 5000000L, "PENDING"));

        assertThatThrownBy(() -> salesOrderService.confirmOnlineOrder(orderId, new ConfirmOnlineOrderRequest()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(SalesErrorCode.ONLINE_ORDER_PAYOS_UNPAID));

        verify(inventoryApi, never()).reserveStock(any(), any());
    }

    @Test
    @DisplayName("UC-22: Từ chối đơn hàng Online đã thanh toán PayOS tự động tạo yêu cầu hoàn tiền cho Kế toán")
    void rejectOnlineOrder_AlreadyPaidViaPayos_RecordsReceiptAndCreatesPendingCustomerRefund() {
        UUID orderId = UUID.randomUUID();
        UUID payosPaymentId = UUID.randomUUID();
        Order onlineOrder = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261125-0003")
                .customerId(customerId)
                .orderType(OrderType.RETAIL.name())
                .orderChannel(OrderChannel.ONLINE.name())
                .orderStatus(OrderStatus.PENDING_CONFIRMATION.name())
                .paymentStatus(PaymentStatus.PAID.name())
                .paymentMethod(PaymentMethod.BANK_TRANSFER.name())
                .totalAmount(new BigDecimal("4000"))
                .build();

        OnlinePayosPayment payosPayment = OnlinePayosPayment.builder()
                .payosPaymentId(payosPaymentId)
                .orderId(orderId)
                .payosOrderCode(88889999L)
                .status("PAID")
                .amount(4000L)
                .amountPaid(4000L)
                .build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(onlineOrder));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(onlineOrder));
        when(onlinePayosPaymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payosPayment));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(salesInvoiceRepository.findByOrderIdAndInvoiceType(orderId, "STORE")).thenReturn(Optional.empty());

        RejectOnlineOrderRequest rejectRequest = RejectOnlineOrderRequest.builder()
                .reason("Sản phẩm tạm ngừng nhập hàng")
                .build();

        SalesOrderDetailResponse response = salesOrderService.rejectOnlineOrder(orderId, rejectRequest);

        assertThat(response.getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(onlineOrder.getNote()).contains("Sản phẩm tạm ngừng nhập hàng");
        verify(orderReceivableApi).ensureOnlinePayosReceiptRecorded(
                eq(orderId), eq(payosPaymentId), eq(88889999L), eq(new BigDecimal("4000")), any(), eq(staffId));
        verify(orderReceivableApi).createPendingCustomerRefund(any());
    }
}
