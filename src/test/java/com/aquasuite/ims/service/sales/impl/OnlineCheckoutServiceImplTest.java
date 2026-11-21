package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerAddressDto;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;
import com.aquasuite.ims.entity.sales.CartItem;
import com.aquasuite.ims.entity.sales.OnlinePayosPayment;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.enums.sales.OrderChannel;
import com.aquasuite.ims.enums.sales.OrderStatus;
import com.aquasuite.ims.enums.sales.OrderType;
import com.aquasuite.ims.enums.sales.PaymentMethod;
import com.aquasuite.ims.enums.sales.PaymentStatus;
import com.aquasuite.ims.exception.sales.SalesErrorCode;
import com.aquasuite.ims.mapper.sales.OnlineCheckoutMapper;
import com.aquasuite.ims.mapper.sales.SalesOrderMapper;
import com.aquasuite.ims.integration.sales.PayosClient;
import com.aquasuite.ims.config.PayosProperties;
import com.aquasuite.ims.dto.sales.request.PlaceOnlineOrderRequest;
import com.aquasuite.ims.dto.sales.response.CartResponse;
import com.aquasuite.ims.dto.sales.response.OnlineCheckoutPreviewResponse;
import com.aquasuite.ims.dto.sales.response.OnlineOrderCheckoutResponse;
import com.aquasuite.ims.repository.sales.CartItemRepository;
import com.aquasuite.ims.repository.sales.OnlinePayosPaymentRepository;
import com.aquasuite.ims.repository.sales.OrderItemRepository;
import com.aquasuite.ims.repository.sales.OrderRepository;
import com.aquasuite.ims.service.sales.impl.CartServiceImpl;
import com.aquasuite.ims.service.sales.helper.OrderCodeGenerator;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnlineCheckoutServiceImplTest {

    @Mock
    private CartServiceImpl cartService;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OnlinePayosPaymentRepository onlinePayosPaymentRepository;

    @Mock
    private PayosClient payosClient;

    @Mock
    private IamQueryService iamApi;

    @Mock
    private CustomerService crmApi;

    @Mock
    private CatalogLookupService catalogApi;

    @Mock
    private InventoryService inventoryApi;

    private PayosProperties payosProperties;
    private OnlineCheckoutServiceImpl onlineCheckoutService;

    private UUID userId;
    private UUID customerId;
    private UUID variantId;
    private UUID productId;
    private UUID addressId;

    @BeforeEach
    void setUp() {
        payosProperties = new PayosProperties();
        payosProperties.setClientId("client-id-123");
        payosProperties.setApiKey("api-key-123");
        payosProperties.setChecksumKey("checksum-123");
        payosProperties.setReturnUrl("http://localhost:5173/checkout/result");
        payosProperties.setCancelUrl("http://localhost:5173/checkout/result");
        payosProperties.setOverrideAmount(2000L);

        OrderCodeGenerator orderCodeGenerator = new OrderCodeGenerator(orderRepository);
        SalesOrderMapper salesOrderMapper = Mappers.getMapper(SalesOrderMapper.class);
        OnlineCheckoutMapper onlineCheckoutMapper = Mappers.getMapper(OnlineCheckoutMapper.class);

        onlineCheckoutService = new OnlineCheckoutServiceImpl(
                cartService,
                cartItemRepository,
                orderRepository,
                orderItemRepository,
                onlinePayosPaymentRepository,
                orderCodeGenerator,
                salesOrderMapper,
                onlineCheckoutMapper,
                payosClient,
                payosProperties,
                iamApi,
                crmApi,
                catalogApi,
                inventoryApi
        );

        userId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        addressId = UUID.randomUUID();
    }

    private void mockAuthenticatedCustomer() {
        when(iamApi.getCurrentUserId()).thenReturn(userId);
        when(crmApi.getCustomerByUserId(userId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder()
                        .customerId(customerId)
                        .userId(userId)
                        .customerCode("CUST-018")
                        .customerName("Nguyễn Bá Lâm")
                        .phone("0901234567")
                        .email("lam@aquasuite.vn")
                        .status("ACTIVE")
                        .build()
        ));
    }

    private CustomerAddressDto buildSavedAddress(boolean isDefault) {
        return CustomerAddressDto.builder()
                .addressId(addressId)
                .customerId(customerId)
                .recipientName("Nguyễn Bá Lâm")
                .phone("0901234567")
                .addressText("123 Nguyễn Văn Linh, Quận 7, TP.HCM")
                .label("Nhà riêng")
                .isDefault(isDefault)
                .build();
    }

    private void mockCartAndInventory(BigDecimal qty, BigDecimal sellingPrice) {
        CartItem cartItem = CartItem.builder()
                .cartItemId(UUID.randomUUID())
                .customerId(customerId)
                .variantId(variantId)
                .quantity(qty)
                .unitPrice(sellingPrice)
                .updatedAt(OffsetDateTime.now())
                .build();
        when(cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId)).thenReturn(List.of(cartItem));

        CatalogSkuSummaryDto sku = CatalogSkuSummaryDto.builder()
                .variantId(variantId)
                .productId(productId)
                .sku("TOTO-SEN-01")
                .productName("Sen cây nhiệt độ TOTO")
                .variantName("Mạ Crom")
                .unit("Bộ")
                .sellingPrice(sellingPrice)
                .status("ACTIVE")
                .build();
        when(catalogApi.getSkuSummaries(Set.of(variantId))).thenReturn(Map.of(variantId, sku));

        StockSummaryDto stock = StockSummaryDto.builder()
                .variantId(variantId)
                .warehouseCode("MAIN")
                .onHandQty(new BigDecimal("20"))
                .reservedQty(BigDecimal.ZERO)
                .availableQty(new BigDecimal("20"))
                .build();
        when(inventoryApi.getStockSummaries(Set.of(variantId))).thenReturn(Map.of(variantId, stock));

        when(orderRepository.countByOrderCodeStartingWith(anyString())).thenReturn(0L);
        when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getCheckoutPreview_returnsCartAddressesDefaultAddressAndPayosFlag() {
        mockAuthenticatedCustomer();
        CustomerAddressDto defaultAddr = buildSavedAddress(true);
        CartResponse cart = CartResponse.builder()
                .customerId(customerId)
                .totalItems(1)
                .totalQuantity(new BigDecimal("2"))
                .subtotal(new BigDecimal("5000000"))
                .items(List.of())
                .build();

        when(cartService.getCurrentCart()).thenReturn(cart);
        when(crmApi.getDeliveryAddressesByCustomerId(customerId)).thenReturn(List.of(defaultAddr));

        OnlineCheckoutPreviewResponse preview = onlineCheckoutService.getCheckoutPreview();

        assertThat(preview.getCart().getSubtotal()).isEqualByComparingTo("5000000");
        assertThat(preview.getSavedAddresses()).hasSize(1);
        assertThat(preview.getDefaultAddress().addressId()).isEqualTo(addressId);
        assertThat(preview.getShippingFee()).isEqualByComparingTo("0");
        assertThat(preview.getGrandTotal()).isEqualByComparingTo("5000000");
        assertThat(preview.isPayosConfigured()).isTrue();
    }

    @Test
    void placeOnlineOrder_cashCod_createsPendingOrderAndClearsCartWithoutPayosCall() {
        mockAuthenticatedCustomer();
        mockCartAndInventory(new BigDecimal("2"), new BigDecimal("2500000"));

        PlaceOnlineOrderRequest request = PlaceOnlineOrderRequest.builder()
                .recipientName("Lâm")
                .recipientPhone("0901234567")
                .deliveryAddress("456 Lê Lợi, Quận 1, TP.HCM")
                .deliverySchedule("Giao buổi chiều")
                .note("Gọi trước khi giao")
                .paymentMethod(PaymentMethod.CASH)
                .build();

        OnlineOrderCheckoutResponse response = onlineCheckoutService.placeOnlineOrder(request);

        assertThat(response.getOrderChannel()).isEqualTo(OrderChannel.ONLINE.name());
        assertThat(response.getOrderType()).isEqualTo(OrderType.RETAIL.name());
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION.name());
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID.name());
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.CASH.name());
        assertThat(response.getTotalAmount()).isEqualByComparingTo("5000000.00");
        assertThat(response.getDeliveryAddress()).contains("456 Lê Lợi, Quận 1, TP.HCM");
        assertThat(response.getPayosPayment()).isNull();

        verify(payosClient, never()).createPaymentLink(any());
        verify(cartItemRepository).deleteAllByCustomerId(customerId);
    }

    @Test
    void placeOnlineOrder_bankTransferPayos_createsPayosLinkSavesPaymentAndClearsCart() {
        mockAuthenticatedCustomer();
        mockCartAndInventory(new BigDecimal("2"), new BigDecimal("2500000"));
        when(crmApi.getDeliveryAddressesByCustomerId(customerId)).thenReturn(List.of(buildSavedAddress(true)));
        when(onlinePayosPaymentRepository.existsByPayosOrderCode(anyLong())).thenReturn(false);
        when(onlinePayosPaymentRepository.saveAndFlush(any(OnlinePayosPayment.class))).thenAnswer(inv -> inv.getArgument(0));

        when(payosClient.createPaymentLink(any())).thenAnswer(inv -> {
            PayosClient.CreatePaymentCommand cmd = inv.getArgument(0);
            return new PayosClient.PaymentLinkResult(
                    cmd.orderCode(),
                    "payos-link-001",
                    "https://pay.payos.vn/web/payos-link-001",
                    "000201010212...",
                    "970448",
                    "0030100067695002",
                    "NGUYEN BA LAM",
                    cmd.amount(),
                    cmd.description(),
                    "PENDING"
            );
        });

        PlaceOnlineOrderRequest request = PlaceOnlineOrderRequest.builder()
                .addressId(addressId)
                .deliverySchedule("Trong ngày")
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

        OnlineOrderCheckoutResponse response = onlineCheckoutService.placeOnlineOrder(request);

        ArgumentCaptor<PayosClient.CreatePaymentCommand> cmdCaptor =
                ArgumentCaptor.forClass(PayosClient.CreatePaymentCommand.class);
        verify(payosClient).createPaymentLink(cmdCaptor.capture());

        PayosClient.CreatePaymentCommand captured = cmdCaptor.getValue();
        assertThat(captured.amount()).isEqualTo(2000L);
        assertThat(captured.returnUrl()).contains("orderId=" + response.getOrderId());

        assertThat(response.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(response.getOrderStatus()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(response.getPaymentStatus()).isEqualTo("UNPAID");
        assertThat(response.getPayosPayment()).isNotNull();
        assertThat(response.getPayosPayment().getCheckoutUrl()).isEqualTo("https://pay.payos.vn/web/payos-link-001");
        assertThat(response.getPayosPayment().getAccountNumber()).isEqualTo("0030100067695002");

        verify(cartItemRepository).deleteAllByCustomerId(customerId);
    }

    @Test
    void placeOnlineOrder_creditMethod_throwsOnlinePaymentMethodInvalid() {
        PlaceOnlineOrderRequest request = PlaceOnlineOrderRequest.builder()
                .deliveryAddress("Hà Nội")
                .paymentMethod(PaymentMethod.CREDIT)
                .build();

        assertThatThrownBy(() -> onlineCheckoutService.placeOnlineOrder(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(SalesErrorCode.ONLINE_PAYMENT_METHOD_INVALID);
    }

    @Test
    void placeOnlineOrder_emptyCart_throwsCartEmptyForCheckout() {
        mockAuthenticatedCustomer();
        when(cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId)).thenReturn(List.of());

        PlaceOnlineOrderRequest request = PlaceOnlineOrderRequest.builder()
                .deliveryAddress("Hà Nội")
                .paymentMethod(PaymentMethod.CASH)
                .build();

        assertThatThrownBy(() -> onlineCheckoutService.placeOnlineOrder(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(SalesErrorCode.CART_EMPTY_FOR_CHECKOUT);
    }

    @Test
    void placeOnlineOrder_missingAddress_throwsDeliveryAddressRequired() {
        mockAuthenticatedCustomer();
        CartItem cartItem = CartItem.builder()
                .cartItemId(UUID.randomUUID())
                .customerId(customerId)
                .variantId(variantId)
                .quantity(BigDecimal.ONE)
                .build();
        when(cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId)).thenReturn(List.of(cartItem));

        PlaceOnlineOrderRequest request = PlaceOnlineOrderRequest.builder()
                .paymentMethod(PaymentMethod.CASH)
                .build();

        assertThatThrownBy(() -> onlineCheckoutService.placeOnlineOrder(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(SalesErrorCode.DELIVERY_ADDRESS_REQUIRED);
    }

    @Test
    void verifyAndSyncPayosPayment_whenPayosPaid_updatesOrderAndPaymentToPaid() {
        mockAuthenticatedCustomer();
        UUID orderId = UUID.randomUUID();
        long payosCode = 1794679999001L;

        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261114-0001")
                .customerId(customerId)
                .orderChannel("ONLINE")
                .orderType("RETAIL")
                .orderStatus("PENDING_CONFIRMATION")
                .paymentStatus("UNPAID")
                .paymentMethod("BANK_TRANSFER")
                .subtotal(new BigDecimal("5000000"))
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("5000000"))
                .build();

        OnlinePayosPayment payosPayment = OnlinePayosPayment.builder()
                .payosPaymentId(UUID.randomUUID())
                .orderId(orderId)
                .payosOrderCode(payosCode)
                .paymentLinkId("link-001")
                .checkoutUrl("https://pay.payos.vn/web/link-001")
                .amount(2000L)
                .amountPaid(0L)
                .status("PENDING")
                .build();

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(onlinePayosPaymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payosPayment));
        when(payosClient.getPaymentLinkInfo(payosCode)).thenReturn(
                new PayosClient.PaymentLinkStatusResult(payosCode, "link-001", 2000L, 2000L, 0L, "PAID")
        );
        when(onlinePayosPaymentRepository.save(any(OnlinePayosPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.<OrderItem>of());

        OnlineOrderCheckoutResponse res = onlineCheckoutService.verifyAndSyncPayosPayment(orderId);

        assertThat(res.getPaymentStatus()).isEqualTo("PAID");
        assertThat(res.getPayosPayment().getStatus()).isEqualTo("PAID");
        assertThat(res.getPayosPayment().getAmountPaid()).isEqualTo(2000L);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrRefreshPayosPaymentLink_whenExistingExpired_replacesWithFreshLink() {
        mockAuthenticatedCustomer();
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261114-0002")
                .customerId(customerId)
                .orderChannel("ONLINE")
                .orderType("RETAIL")
                .orderStatus("PENDING_CONFIRMATION")
                .paymentStatus("UNPAID")
                .paymentMethod("BANK_TRANSFER")
                .totalAmount(new BigDecimal("3000000"))
                .deliveryAddress("TP.HCM")
                .build();

        OnlinePayosPayment expiredPayment = OnlinePayosPayment.builder()
                .payosPaymentId(UUID.randomUUID())
                .orderId(orderId)
                .payosOrderCode(111222333L)
                .status("EXPIRED")
                .build();

        OrderItem orderItem = OrderItem.builder()
                .orderItemId(UUID.randomUUID())
                .orderId(orderId)
                .variantId(variantId)
                .sku("TOTO-SEN-01")
                .productName("Sen cây TOTO")
                .variantName("Mạ Crom")
                .quantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal("3000000"))
                .lineTotal(new BigDecimal("3000000"))
                .build();

        when(orderRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(onlinePayosPaymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(expiredPayment));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
        when(onlinePayosPaymentRepository.existsByPayosOrderCode(anyLong())).thenReturn(false);
        when(onlinePayosPaymentRepository.saveAndFlush(any(OnlinePayosPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payosClient.createPaymentLink(any())).thenReturn(
                new PayosClient.PaymentLinkResult(
                        999888777L,
                        "new-link-002",
                        "https://pay.payos.vn/web/new-link-002",
                        "qr-new",
                        "970448",
                        "0030100067695002",
                        "NGUYEN BA LAM",
                        2000L,
                        "AQS SO202611140002",
                        "PENDING"
                )
        );

        OnlineOrderCheckoutResponse res = onlineCheckoutService.createOrRefreshPayosPaymentLink(orderId);

        verify(onlinePayosPaymentRepository).delete(expiredPayment);
        assertThat(res.getPayosPayment().getPaymentLinkId()).isEqualTo("new-link-002");
        assertThat(res.getPayosPayment().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void getMyOnlineOrders_returnsOrdersWithItemsAndPayosPayment() {
        mockAuthenticatedCustomer();
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("SO-20261118-0099")
                .customerId(customerId)
                .orderChannel(OrderChannel.ONLINE.name())
                .orderType(OrderType.RETAIL.name())
                .orderStatus(OrderStatus.PENDING_CONFIRMATION.name())
                .paymentStatus(PaymentStatus.UNPAID.name())
                .paymentMethod(PaymentMethod.BANK_TRANSFER.name())
                .subtotal(new BigDecimal("4000"))
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("4000"))
                .deliveryAddress("123 Lê Lợi")
                .createdAt(OffsetDateTime.now())
                .build();
        OrderItem item = OrderItem.builder()
                .orderItemId(UUID.randomUUID())
                .orderId(orderId)
                .variantId(variantId)
                .sku("PK-GIOANG-SI21")
                .productName("Gioăng silicon")
                .quantity(new BigDecimal("2"))
                .unitPrice(new BigDecimal("2000"))
                .lineTotal(new BigDecimal("4000"))
                .build();

        when(orderRepository.findByCustomerIdAndOrderChannelOrderByCreatedAtDesc(
                customerId, OrderChannel.ONLINE.name())).thenReturn(List.of(order));
        when(orderItemRepository.findByOrderIdIn(List.of(orderId))).thenReturn(List.of(item));
        when(onlinePayosPaymentRepository.findByOrderIdIn(List.of(orderId))).thenReturn(List.of());

        List<OnlineOrderCheckoutResponse> result = onlineCheckoutService.getMyOnlineOrders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderCode()).isEqualTo("SO-20261118-0099");
        assertThat(result.get(0).getItems()).hasSize(1);
    }
}
