package com.aquasuite.ims.controller.sales;

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
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.mapper.sales.CartMapper;
import com.aquasuite.ims.mapper.sales.OnlineCheckoutMapper;
import com.aquasuite.ims.mapper.sales.SalesOrderMapper;
import com.aquasuite.ims.integration.sales.PayosClient;
import com.aquasuite.ims.config.PayosProperties;
import com.aquasuite.ims.repository.sales.CartItemRepository;
import com.aquasuite.ims.repository.sales.OnlinePayosPaymentRepository;
import com.aquasuite.ims.repository.sales.OrderItemRepository;
import com.aquasuite.ims.repository.sales.OrderRepository;
import com.aquasuite.ims.service.sales.impl.CartCheckoutServiceImpl;
import com.aquasuite.ims.service.sales.helper.OrderCodeGenerator;
import com.aquasuite.ims.service.sales.impl.CartServiceImpl;
import com.aquasuite.ims.service.sales.impl.OnlineCheckoutServiceImpl;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mapstruct.factory.Mappers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bộ kiểm thử giả lập Postman End-to-End cho toàn bộ REST API của UC-18 (Đặt hàng & Thanh toán PayOS).
 * Chạy xuyên suốt OnlineCheckoutController -> OnlineCheckoutServiceImpl -> Mapper -> GlobalExceptionHandler
 * với In-Memory Stateful Store mô phỏng cart_items, orders, order_items, online_payos_payments và PayOS Gateway.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OnlineCheckoutPostmanSimulationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final Map<UUID, CartItem> cartStore = new LinkedHashMap<>();
    private final Map<UUID, com.aquasuite.ims.entity.sales.Order> orderStore = new LinkedHashMap<>();
    private final Map<UUID, List<OrderItem>> orderItemStore = new LinkedHashMap<>();
    private final Map<UUID, OnlinePayosPayment> payosStore = new LinkedHashMap<>();
    private final AtomicReference<String> simulatedPayosRemoteStatus = new AtomicReference<>("PENDING");

    private UUID customerUserId;
    private UUID customerId;
    private UUID savedAddressId;
    private UUID skuToiletId;
    private UUID payosOrderId;

    @BeforeAll
    @SuppressWarnings("unchecked")
    void setupSimulation() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        CartItemRepository cartItemRepository = Mockito.mock(CartItemRepository.class);
        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = Mockito.mock(OrderItemRepository.class);
        OnlinePayosPaymentRepository onlinePayosPaymentRepository = Mockito.mock(OnlinePayosPaymentRepository.class);
        PayosClient payosClient = Mockito.mock(PayosClient.class);
        IamQueryService iamApi = Mockito.mock(IamQueryService.class);
        CustomerService crmApi = Mockito.mock(CustomerService.class);
        CatalogLookupService catalogApi = Mockito.mock(CatalogLookupService.class);
        InventoryService inventoryApi = Mockito.mock(InventoryService.class);

        customerUserId = UUID.fromString("11111111-1111-1111-1111-000000000018");
        customerId = UUID.fromString("22222222-2222-2222-2222-000000000018");
        savedAddressId = UUID.fromString("33333333-3333-3333-3333-000000000018");
        skuToiletId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000018");

        when(iamApi.getCurrentUserId()).thenReturn(customerUserId);
        when(crmApi.getCustomerByUserId(customerUserId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder()
                        .customerId(customerId)
                        .userId(customerUserId)
                        .customerCode("KH-UC18")
                        .customerName("Nguyễn Bá Lâm")
                        .phone("0901234567")
                        .email("lam@aquasuite.vn")
                        .status("ACTIVE")
                        .build()
        ));

        CustomerAddressDto defaultAddress = CustomerAddressDto.builder()
                .addressId(savedAddressId)
                .customerId(customerId)
                .recipientName("Nguyễn Bá Lâm")
                .phone("0901234567")
                .province("TP.HCM")
                .district("Quận 7")
                .ward("Tân Phong")
                .streetAddress("123 Nguyễn Văn Linh")
                .addressText("123 Nguyễn Văn Linh, P. Tân Phong, Q.7, TP.HCM")
                .label("Nhà riêng")
                .isDefault(true)
                .build();
        when(crmApi.getDeliveryAddressesByCustomerId(customerId)).thenReturn(List.of(defaultAddress));

        CatalogSkuSummaryDto toiletSku = CatalogSkuSummaryDto.builder()
                .variantId(skuToiletId)
                .productId(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000018"))
                .sku("TOTO-NEOREST-01")
                .productName("Bồn cầu thông minh TOTO Neorest")
                .variantName("Trắng sứ")
                .unit("Bộ")
                .sellingPrice(new BigDecimal("18500000"))
                .status("ACTIVE")
                .build();
        Map<UUID, CatalogSkuSummaryDto> skuMap = Map.of(skuToiletId, toiletSku);
        when(catalogApi.getSkuSummaries(any(Collection.class))).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            Map<UUID, CatalogSkuSummaryDto> res = new LinkedHashMap<>();
            for (UUID id : ids) {
                if (skuMap.containsKey(id)) {
                    res.put(id, skuMap.get(id));
                }
            }
            return res;
        });

        StockSummaryDto stock = StockSummaryDto.builder()
                .variantId(skuToiletId)
                .warehouseCode("MAIN")
                .onHandQty(new BigDecimal("15"))
                .reservedQty(BigDecimal.ZERO)
                .availableQty(new BigDecimal("15"))
                .build();
        Map<UUID, StockSummaryDto> stockMap = Map.of(skuToiletId, stock);
        when(inventoryApi.getStockSummaries(any(Collection.class))).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            Map<UUID, StockSummaryDto> res = new LinkedHashMap<>();
            for (UUID id : ids) {
                if (stockMap.containsKey(id)) {
                    res.put(id, stockMap.get(id));
                }
            }
            return res;
        });

        seedCartItem(new BigDecimal("1"), new BigDecimal("18500000"));

        when(cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenAnswer(inv -> new ArrayList<>(cartStore.values()));
        doAnswer(inv -> {
            cartStore.clear();
            return null;
        }).when(cartItemRepository).deleteAllByCustomerId(customerId);

        when(orderRepository.countByOrderCodeStartingWith(anyString()))
                .thenAnswer(inv -> (long) orderStore.size());
        when(orderRepository.existsByOrderCode(anyString()))
                .thenAnswer(inv -> {
                    String code = inv.getArgument(0);
                    return orderStore.values().stream().anyMatch(o -> code.equals(o.getOrderCode()));
                });
        when(orderRepository.saveAndFlush(any(com.aquasuite.ims.entity.sales.Order.class)))
                .thenAnswer(inv -> {
                    com.aquasuite.ims.entity.sales.Order o = inv.getArgument(0);
                    orderStore.put(o.getOrderId(), o);
                    return o;
                });
        when(orderRepository.save(any(com.aquasuite.ims.entity.sales.Order.class)))
                .thenAnswer(inv -> {
                    com.aquasuite.ims.entity.sales.Order o = inv.getArgument(0);
                    orderStore.put(o.getOrderId(), o);
                    return o;
                });
        when(orderRepository.findByOrderIdAndCustomerId(any(UUID.class), any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID id = inv.getArgument(0);
                    UUID custId = inv.getArgument(1);
                    com.aquasuite.ims.entity.sales.Order o = orderStore.get(id);
                    if (o != null && custId.equals(o.getCustomerId())) {
                        return Optional.of(o);
                    }
                    return Optional.empty();
                });

        when(orderItemRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> {
            List<OrderItem> list = inv.getArgument(0);
            if (!list.isEmpty()) {
                orderItemStore.put(list.get(0).getOrderId(), new ArrayList<>(list));
            }
            return list;
        });
        when(orderItemRepository.findByOrderId(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return orderItemStore.getOrDefault(id, List.of());
        });

        when(onlinePayosPaymentRepository.existsByPayosOrderCode(anyLong())).thenReturn(false);
        when(onlinePayosPaymentRepository.saveAndFlush(any(OnlinePayosPayment.class))).thenAnswer(inv -> {
            OnlinePayosPayment p = inv.getArgument(0);
            if (p.getPayosPaymentId() == null) {
                p.setPayosPaymentId(UUID.randomUUID());
            }
            payosStore.put(p.getOrderId(), p);
            return p;
        });
        when(onlinePayosPaymentRepository.save(any(OnlinePayosPayment.class))).thenAnswer(inv -> {
            OnlinePayosPayment p = inv.getArgument(0);
            payosStore.put(p.getOrderId(), p);
            return p;
        });
        when(onlinePayosPaymentRepository.findByOrderId(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(payosStore.get(id));
        });

        when(payosClient.createPaymentLink(any())).thenAnswer(inv -> {
            PayosClient.CreatePaymentCommand cmd = inv.getArgument(0);
            return new PayosClient.PaymentLinkResult(
                    cmd.orderCode(),
                    "payos-sim-link-001",
                    "https://pay.payos.vn/web/payos-sim-link-001",
                    "00020101021238570010A0000007270127000697044801130030100067695",
                    "970448",
                    "0030100067695002",
                    "NGUYEN BA LAM",
                    cmd.amount(),
                    cmd.description(),
                    "PENDING"
            );
        });
        when(payosClient.getPaymentLinkInfo(anyLong())).thenAnswer(inv -> {
            long code = inv.getArgument(0);
            String st = simulatedPayosRemoteStatus.get();
            long paid = "PAID".equals(st) ? 2000L : 0L;
            long remaining = "PAID".equals(st) ? 0L : 2000L;
            return new PayosClient.PaymentLinkStatusResult(code, "payos-sim-link-001", 2000L, paid, remaining, st);
        });

        PayosProperties payosProperties = new PayosProperties();
        payosProperties.setClientId("client-sim-123");
        payosProperties.setApiKey("api-sim-123");
        payosProperties.setChecksumKey("checksum-sim-123");
        payosProperties.setReturnUrl("http://localhost:5173/checkout/result");
        payosProperties.setCancelUrl("http://localhost:5173/checkout/result");
        payosProperties.setOverrideAmount(2000L);

        CartMapper cartMapper = Mappers.getMapper(CartMapper.class);
        SalesOrderMapper salesOrderMapper = Mappers.getMapper(SalesOrderMapper.class);
        OnlineCheckoutMapper onlineCheckoutMapper = Mappers.getMapper(OnlineCheckoutMapper.class);

        CartServiceImpl cartService = new CartServiceImpl(
                cartItemRepository,
                cartMapper,
                iamApi,
                crmApi,
                catalogApi,
                inventoryApi
        );

        OnlineCheckoutServiceImpl onlineCheckoutService = new OnlineCheckoutServiceImpl(
                cartService,
                cartItemRepository,
                orderRepository,
                orderItemRepository,
                onlinePayosPaymentRepository,
                new OrderCodeGenerator(orderRepository),
                salesOrderMapper,
                onlineCheckoutMapper,
                payosClient,
                payosProperties,
                iamApi,
                crmApi,
                catalogApi,
                inventoryApi
        );

        mockMvc = MockMvcBuilders.standaloneSetup(new CartCheckoutController(new CartCheckoutServiceImpl(null, onlineCheckoutService)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private void seedCartItem(BigDecimal quantity, BigDecimal price) {
        UUID id = UUID.randomUUID();
        cartStore.put(id, CartItem.builder()
                .cartItemId(id)
                .customerId(customerId)
                .variantId(skuToiletId)
                .quantity(quantity)
                .unitPrice(price)
                .updatedAt(OffsetDateTime.now())
                .build());
    }

    @Test
    @Order(1)
    @DisplayName("1. [GET /checkout-preview] Xem trước giỏ hàng + sổ địa chỉ -> 200 OK")
    void step1_GetCheckoutPreview() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sales/online-orders/checkout-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cart.totalItems").value(1))
                .andExpect(jsonPath("$.data.grandTotal").value(18500000))
                .andExpect(jsonPath("$.data.defaultAddress.addressId").value(savedAddressId.toString()))
                .andExpect(jsonPath("$.data.payosConfigured").value(true))
                .andReturn();
        printHttpResult("1. GET /api/v1/sales/online-orders/checkout-preview", null, result);
    }

    @Test
    @Order(2)
    @DisplayName("2. [POST /online-orders] Chặn mua nợ CREDIT trên kênh Online -> 400 SALES_030")
    void step2_RejectCreditMethod() throws Exception {
        String body = """
                {
                  "addressId": "%s",
                  "paymentMethod": "CREDIT"
                }
                """.formatted(savedAddressId);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/online-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SALES_030"))
                .andReturn();
        printHttpResult("2. POST /api/v1/sales/online-orders (CREDIT -> 400)", body, result);
    }

    @Test
    @Order(3)
    @DisplayName("3. [POST /online-orders] Đặt hàng PayOS (BANK_TRANSFER) -> 200 OK, tạo QR & xóa giỏ hàng")
    void step3_PlacePayosOnlineOrder() throws Exception {
        String body = """
                {
                  "addressId": "%s",
                  "deliverySchedule": "Giao sáng mai 9h-11h",
                  "note": "Khách thanh toán PayOS OCB",
                  "paymentMethod": "BANK_TRANSFER"
                }
                """.formatted(savedAddressId);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/online-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderChannel").value("ONLINE"))
                .andExpect(jsonPath("$.data.orderType").value("RETAIL"))
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.paymentMethod").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.data.payosPayment.checkoutUrl").value("https://pay.payos.vn/web/payos-sim-link-001"))
                .andExpect(jsonPath("$.data.payosPayment.accountNumber").value("0030100067695002"))
                .andExpect(jsonPath("$.data.payosPayment.amount").value(2000))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        payosOrderId = UUID.fromString(objectMapper.readTree(json).path("data").path("orderId").asText());
        printHttpResult("3. POST /api/v1/sales/online-orders (BANK_TRANSFER PayOS)", body, result);
    }

    @Test
    @Order(4)
    @DisplayName("4. [GET /online-orders/{orderId}] Lấy chi tiết đơn hàng & link PayOS -> 200 OK")
    void step4_GetOnlineOrderDetail() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sales/online-orders/{orderId}", payosOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(payosOrderId.toString()))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.items[0].sku").value("TOTO-NEOREST-01"))
                .andReturn();
        printHttpResult("4. GET /api/v1/sales/online-orders/" + payosOrderId, null, result);
    }

    @Test
    @Order(5)
    @DisplayName("5. [POST /online-orders/{orderId}/payos/verify] Đối soát PayOS PAID -> Cập nhật đơn thành PAID")
    void step5_VerifyAndSyncPayosPaid() throws Exception {
        simulatedPayosRemoteStatus.set("PAID");

        MvcResult result = mockMvc.perform(post("/api/v1/sales/online-orders/{orderId}/payos/verify", payosOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.payosPayment.status").value("PAID"))
                .andExpect(jsonPath("$.data.payosPayment.amountPaid").value(2000))
                .andReturn();
        printHttpResult("5. POST /api/v1/sales/online-orders/" + payosOrderId + "/payos/verify", null, result);
    }

    @Test
    @Order(6)
    @DisplayName("6. [POST /online-orders/{orderId}/payos/payment-link] Đơn đã PAID không tạo lại link -> 400 SALES_033")
    void step6_RejectRefreshLinkAfterPaid() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sales/online-orders/{orderId}/payos/payment-link", payosOrderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SALES_033"))
                .andReturn();
        printHttpResult("6. POST /api/v1/sales/online-orders/{orderId}/payos/payment-link (Already PAID)", null, result);
    }

    @Test
    @Order(7)
    @DisplayName("7. [POST /online-orders] Đặt hàng COD (CASH) với địa chỉ nhập tay -> 200 OK")
    void step7_PlaceCodOrderManualAddress() throws Exception {
        seedCartItem(new BigDecimal("2"), new BigDecimal("18500000"));
        String body = """
                {
                  "recipientName": "Nguyễn Bá Lâm",
                  "recipientPhone": "0909888777",
                  "deliveryAddress": "45 Lê Văn Lương, Hà Nội",
                  "note": "Thanh toán tiền mặt khi nhận hàng",
                  "paymentMethod": "CASH"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/sales/online-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.data.totalAmount").value(37000000.00))
                .andExpect(jsonPath("$.data.payosPayment").doesNotExist())
                .andReturn();
        printHttpResult("7. POST /api/v1/sales/online-orders (CASH COD)", body, result);
    }

    @Test
    @Order(8)
    @DisplayName("8. [POST /online-orders] Đặt hàng khi giỏ hàng đã trống -> 400 SALES_028")
    void step8_RejectEmptyCartCheckout() throws Exception {
        String body = """
                {
                  "addressId": "%s",
                  "paymentMethod": "CASH"
                }
                """.formatted(savedAddressId);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/online-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SALES_028"))
                .andReturn();
        printHttpResult("8. POST /api/v1/sales/online-orders (Empty Cart -> 400)", body, result);
    }

    private void printHttpResult(String title, String requestBody, MvcResult result) throws Exception {
        System.out.println("\n==================== " + title + " ====================");
        if (requestBody != null) {
            System.out.println("Request Body:\n" + requestBody.trim());
        }
        System.out.println("HTTP Status: " + result.getResponse().getStatus());
        System.out.println("Response Body:\n" + result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
