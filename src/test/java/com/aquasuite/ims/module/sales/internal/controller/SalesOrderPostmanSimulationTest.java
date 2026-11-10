package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogBundleSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.finance.api.CreditControlApi;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.enums.FulfillmentMethod;
import com.aquasuite.ims.module.sales.internal.enums.OrderChannel;
import com.aquasuite.ims.module.sales.internal.enums.OrderType;
import com.aquasuite.ims.module.sales.internal.enums.PaymentMethod;
import com.aquasuite.ims.module.sales.internal.mapper.SalesOrderMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateComboItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateOrderItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationRepository;
import com.aquasuite.ims.module.sales.internal.service.SalesOrderService;
import com.aquasuite.ims.module.sales.internal.service.helper.OrderCodeGenerator;
import com.aquasuite.ims.module.sales.internal.service.impl.SalesOrderApiImpl;
import com.aquasuite.ims.module.sales.internal.service.impl.SalesOrderServiceImpl;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mapstruct.factory.Mappers;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bộ kiểm thử giả lập Postman End-to-End cho toàn bộ API của UC-21 (Create and Manage Sales Orders)
 * và Contract tích hợp với UC-23 (Manage Payment - Nam) & UC-70 (Verify Bank Transfer).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SalesOrderPostmanSimulationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private SalesOrderApi salesOrderApi;

    // In-memory stateful storage để giả lập Database thực tế xuyên suốt các bước Postman
    private final Map<UUID, Order> orderStore = new LinkedHashMap<>();
    private final Map<UUID, List<OrderItem>> orderItemStore = new LinkedHashMap<>();
    private final AtomicInteger orderSeq = new AtomicInteger(1);

    // Dữ liệu mẫu cố định
    private UUID retailCustomerId;
    private UUID overdueCustomerId;
    private UUID staffId;
    private UUID skuToiletId;
    private UUID skuBasinId;
    private UUID comboBathroomId;
    private UUID createdOrderIdForPaymentFlow;

    @BeforeAll
    @SuppressWarnings("unchecked")
    void setupSimulation() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = Mockito.mock(OrderItemRepository.class);
        var salesInvoiceRepository = Mockito.mock(com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository.class);
        var salesInvoiceItemRepository = Mockito.mock(com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceItemRepository.class);
        CreditControlApi creditControlApi = Mockito.mock(CreditControlApi.class);
        CatalogApi catalogApi = Mockito.mock(CatalogApi.class);
        InventoryApi inventoryApi = Mockito.mock(InventoryApi.class);
        CrmApi crmApi = Mockito.mock(CrmApi.class);
        IamApi iamApi = Mockito.mock(IamApi.class);
        OrderCodeGenerator orderCodeGenerator = Mockito.mock(OrderCodeGenerator.class);
        QuotationRepository quotationRepository = Mockito.mock(QuotationRepository.class);
        QuotationItemRepository quotationItemRepository = Mockito.mock(QuotationItemRepository.class);
        var quotationPaymentMilestoneRepository = Mockito.mock(com.aquasuite.ims.module.sales.internal.repository.QuotationPaymentMilestoneRepository.class);
        var orderPaymentMilestoneRepository = Mockito.mock(com.aquasuite.ims.module.sales.internal.repository.OrderPaymentMilestoneRepository.class);
        SalesOrderMapper salesOrderMapper = Mappers.getMapper(SalesOrderMapper.class);

        retailCustomerId = UUID.fromString("11111111-2222-3333-4444-555555555501");
        overdueCustomerId = UUID.fromString("11111111-2222-3333-4444-555555555502");
        staffId = UUID.fromString("99999999-8888-7777-6666-555555555599");
        skuToiletId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000001");
        skuBasinId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000002");
        comboBathroomId = UUID.fromString("cccccccc-bbbb-cccc-dddd-000000000009");

        // Mock CRM
        CustomerSummaryDto retailCustomer = CustomerSummaryDto.builder()
                .customerId(retailCustomerId)
                .customerCode("CUS-2026-001")
                .customerName("Nguyễn Văn Lâm (Khách Lẻ Showroom)")
                .phone("0901234567")
                .status("ACTIVE")
                .build();

        CustomerSummaryDto overdueCustomer = CustomerSummaryDto.builder()
                .customerId(overdueCustomerId)
                .customerCode("CUS-2026-002")
                .customerName("Công Ty Xây Dựng Nợ Quá Hạn")
                .phone("0909999888")
                .status("ACTIVE")
                .build();

        when(crmApi.getCustomerById(retailCustomerId)).thenReturn(Optional.of(retailCustomer));
        when(crmApi.getCustomerById(overdueCustomerId)).thenReturn(Optional.of(overdueCustomer));
        when(crmApi.getOrCreateWalkInCustomer()).thenReturn(retailCustomer);
        when(crmApi.getOrCreateWalkInCustomer(any(), any())).thenReturn(retailCustomer);
        when(creditControlApi.authorize(any())).thenAnswer(invocation -> {
            var command = invocation.getArgument(0, com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationCommand.class);
            boolean approved = !overdueCustomerId.equals(command.customerId());
            return new com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationDecision(
                    approved, approved ? "APPROVED" : "OVERDUE", approved ? new BigDecimal("100000000") : BigDecimal.ZERO);
        });

        // Mock IAM
        when(iamApi.getCurrentUserId()).thenReturn(staffId);
        when(iamApi.getUserById(staffId)).thenReturn(UserSummaryDto.builder()
                .id(staffId)
                .username("sales.staff")
                .fullName("Trần Thị Thu Ngân (Sales)")
                .email("sales.staff@aquasuite.com")
                .phone("0988111222")
                .status("ACTIVE")
                .roles(List.of("SALES_STAFF"))
                .build());

        // Mock Catalog SKUs & Combo
        CatalogSkuSummaryDto toiletSku = CatalogSkuSummaryDto.builder()
                .variantId(skuToiletId)
                .sku("TOTO-NEOREST-01")
                .productName("Bồn cầu thông minh TOTO Neorest")
                .variantName("Màu trắng sứ - Tự động xả")
                .sellingPrice(new BigDecimal("5000000"))
                .status("ACTIVE")
                .build();

        CatalogSkuSummaryDto basinSku = CatalogSkuSummaryDto.builder()
                .variantId(skuBasinId)
                .sku("INAX-LAVABO-02")
                .productName("Chậu rửa đặt bàn INAX")
                .variantName("Men Aqua Ceramic chống bám bẩn")
                .sellingPrice(new BigDecimal("3000000"))
                .status("ACTIVE")
                .build();

        CatalogBundleSummaryDto masterCombo = CatalogBundleSummaryDto.builder()
                .bundleId(comboBathroomId)
                .bundleName("Combo Phòng Tắm Master Cao Cấp")
                .packagePrice(new BigDecimal("8000000"))
                .status("ACTIVE")
                .items(List.of(
                        CatalogBundleSummaryDto.CatalogBundleItemDto.builder()
                                .variantId(skuToiletId)
                                .sku("TOTO-NEOREST-01")
                                .variantName("Màu trắng sứ - Tự động xả")
                                .quantity(1)
                                .sellingPrice(new BigDecimal("5000000"))
                                .build(),
                        CatalogBundleSummaryDto.CatalogBundleItemDto.builder()
                                .variantId(skuBasinId)
                                .sku("INAX-LAVABO-02")
                                .variantName("Men Aqua Ceramic chống bám bẩn")
                                .quantity(1)
                                .sellingPrice(new BigDecimal("3000000"))
                                .build()
                ))
                .build();

        when(catalogApi.getSkuSummary(skuToiletId)).thenReturn(Optional.of(toiletSku));
        when(catalogApi.getSkuSummary(skuBasinId)).thenReturn(Optional.of(basinSku));
        when(catalogApi.getBundleSummary(comboBathroomId)).thenReturn(Optional.of(masterCombo));

        // Mock Inventory: Cho phép tồn kho <= 20, nếu > 20 thì báo thiếu hàng (INSUFFICIENT_STOCK)
        when(inventoryApi.checkAvailability(any(UUID.class), any(BigDecimal.class))).thenAnswer(inv -> {
            BigDecimal qty = inv.getArgument(1);
            return qty.compareTo(new BigDecimal("20")) <= 0;
        });

        // Mock OrderCodeGenerator
        when(orderCodeGenerator.generateOrderCode()).thenAnswer(inv ->
                String.format("SO-20261103-%04d", orderSeq.getAndIncrement())
        );

        // Mock OrderRepository & OrderItemRepository (In-Memory Stateful)
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            orderStore.put(o.getOrderId(), o);
            return o;
        });
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            orderStore.put(o.getOrderId(), o);
            return o;
        });
        when(orderRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(orderStore.get(id));
        });
        org.mockito.Mockito.lenient().when(orderRepository.findForPaymentUpdate(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(orderStore.get(id));
        });
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(inv -> {
            Pageable pageable = inv.getArgument(1);
            List<Order> list = new ArrayList<>(orderStore.values());
            return new PageImpl<>(list, pageable, list.size());
        });
        when(orderItemRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> {
            List<OrderItem> items = inv.getArgument(0);
            if (!items.isEmpty()) {
                UUID ordId = items.get(0).getOrderId();
                orderItemStore.put(ordId, new ArrayList<>(items));
            }
            return items;
        });
        when(orderItemRepository.findByOrderId(any(UUID.class))).thenAnswer(inv -> {
            UUID ordId = inv.getArgument(0);
            return orderItemStore.getOrDefault(ordId, List.of());
        });

        SalesOrderService salesOrderService = new SalesOrderServiceImpl(
                orderRepository,
                orderItemRepository,
                salesInvoiceRepository,
                salesInvoiceItemRepository,
                creditControlApi,
                catalogApi,
                inventoryApi,
                crmApi,
                iamApi,
                salesOrderMapper,
                orderCodeGenerator,
                quotationRepository,
                quotationItemRepository,
                quotationPaymentMilestoneRepository,
                orderPaymentMilestoneRepository
        );

        salesOrderApi = new SalesOrderApiImpl(orderRepository, salesInvoiceRepository, orderPaymentMilestoneRepository);
        SalesOrderController controller = new SalesOrderController(salesOrderService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("1. [POST /api/v1/sales/orders] Tạo đơn bán lẻ tại quầy với SKU lẻ (10.000.000đ) -> 201 Created, PROCESSING & UNPAID")
    void test1_CreateRetailOrder_IndividualSku_Success() throws Exception {
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(retailCustomerId)
                .orderType(OrderType.RETAIL)
                .orderChannel(OrderChannel.COUNTER)
                .fulfillmentMethod(FulfillmentMethod.IN_STORE_PICKUP)
                .paymentMethod(PaymentMethod.CASH)
                .discountAmount(BigDecimal.ZERO)
                .deliveryAddress("Nhận trực tiếp tại Showroom AquaSuite")
                .note("Đơn bán lẻ tại quầy - Chuyển sang UC-23 để thu tiền")
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .variantId(skuToiletId)
                                .quantity(new BigDecimal("2"))
                                .discountAmount(BigDecimal.ZERO)
                                .build()
                ))
                .build();

        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Sales order created successfully."))
                .andExpect(jsonPath("$.data.orderCode").value("SO-20261103-0001"))
                .andExpect(jsonPath("$.data.orderStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.totalAmount").value(10000000))
                .andReturn();

        createdOrderIdForPaymentFlow = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .path("data").path("orderId").asText()
        );

        printHttpResult("1. POST /api/v1/sales/orders (Tạo đơn bán lẻ SKU lẻ - 10 triệu)", reqJson, result);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("2. [POST /api/v1/sales/orders] Tạo đơn bán lẻ gói Combo + Khuyến mãi giảm giá -> 201 Created")
    void test2_CreateRetailOrder_WithComboAndDiscount_Success() throws Exception {
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(retailCustomerId)
                .orderType(OrderType.RETAIL)
                .orderChannel(OrderChannel.COUNTER)
                .fulfillmentMethod(FulfillmentMethod.SCHEDULED_DELIVERY)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .discountAmount(new BigDecimal("500000"))
                .deliveryAddress("Số 88 Láng Hạ, Đống Đa, Hà Nội")
                .note("Giao hẹn giờ lắp đặt Combo phòng tắm")
                .combos(List.of(
                        CreateComboItemRequest.builder()
                                .comboId(comboBathroomId)
                                .quantity(1)
                                .build()
                ))
                .build();

        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("SO-20261103-0002"))
                .andExpect(jsonPath("$.data.subtotal").value(8000000))
                .andExpect(jsonPath("$.data.discountAmount").value(500000))
                .andExpect(jsonPath("$.data.totalAmount").value(7500000))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andReturn();

        printHttpResult("2. POST /api/v1/sales/orders (Tạo đơn gói Combo + Chiết khấu)", reqJson, result);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("3. [POST /api/v1/sales/orders] Tạo đơn mua nợ sau Finance authorization -> 201 Created")
    void test3_CreateRetailOrder_PurchaseOnCredit_Success() throws Exception {
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(retailCustomerId)
                .orderType(OrderType.RETAIL)
                .orderChannel(OrderChannel.COUNTER)
                .fulfillmentMethod(FulfillmentMethod.SCHEDULED_DELIVERY)
                .paymentMethod(PaymentMethod.CREDIT)
                .deliveryAddress("Khu đô thị Vinhomes Smart City, Hà Nội")
                .note("Khách mua công nợ 30 ngày")
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .variantId(skuBasinId)
                                .quantity(new BigDecimal("3"))
                                .build()
                ))
                .build();

        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("SO-20261103-0003"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.dueDate").doesNotExist())
                .andReturn();

        printHttpResult("3. POST /api/v1/sales/orders (Mua công nợ CREDIT hợp lệ - Chưa tính hạn hóa đơn điện tử)", reqJson, result);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("4. [POST /api/v1/sales/orders] Lỗi Validation: Đơn hàng rỗng không có SKU hay Combo -> 400 Bad Request (SALES_010)")
    void test4_CreateRetailOrder_EmptyItems_Returns400() throws Exception {
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(retailCustomerId)
                .fulfillmentMethod(FulfillmentMethod.IN_STORE_PICKUP)
                .paymentMethod(PaymentMethod.CASH)
                .items(List.of())
                .combos(List.of())
                .build();

        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SALES_010"))
                .andReturn();

        printHttpResult("4. POST /api/v1/sales/orders (Lỗi đơn hàng trống - SALES_010)", reqJson, result);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("5. [POST /api/v1/sales/orders] Lỗi Nghiệp vụ: Tồn kho khả dụng không đủ -> 400 Bad Request (SALES_001)")
    void test5_CreateRetailOrder_InsufficientStock_Returns400() throws Exception {
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(retailCustomerId)
                .fulfillmentMethod(FulfillmentMethod.IN_STORE_PICKUP)
                .paymentMethod(PaymentMethod.CASH)
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .variantId(skuToiletId)
                                .quantity(new BigDecimal("99")) // Vượt quá tồn kho khả dụng (20)
                                .build()
                ))
                .build();

        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SALES_001"))
                .andReturn();

        printHttpResult("5. POST /api/v1/sales/orders (Lỗi vượt tồn kho khả dụng - SALES_001)", reqJson, result);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("6. [POST /api/v1/sales/orders] Lỗi Nghiệp vụ: Chặn mua nợ do khách có nợ quá hạn > 60 ngày -> 400 Bad Request (SALES_002 / MSG63)")
    void test6_CreateRetailOrder_CreditOverdueBlocked_Returns400() throws Exception {
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .customerId(overdueCustomerId)
                .fulfillmentMethod(FulfillmentMethod.SCHEDULED_DELIVERY)
                .paymentMethod(PaymentMethod.CREDIT)
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .variantId(skuToiletId)
                                .quantity(BigDecimal.ONE)
                                .build()
                ))
                .build();

        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SALES_002"))
                .andExpect(jsonPath("$.message").value("Credit limit exceeded or overdue balance detected. Order on credit is blocked."))
                .andReturn();

        printHttpResult("6. POST /api/v1/sales/orders (Chặn mua nợ quá hạn > 60 ngày - MSG63)", reqJson, result);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("7. [GET /api/v1/sales/orders] Lấy danh sách đơn hàng bán lẻ có phân trang & bộ lọc -> 200 OK")
    void test7_GetSalesOrders_PaginatedList_Success() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sales/orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("orderStatus", "PROCESSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andReturn();

        printHttpResult("7. GET /api/v1/sales/orders?page=0&size=10&orderStatus=PROCESSING", null, result);
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("8. [GET /api/v1/sales/orders/{orderId}] Xem chi tiết đơn hàng vừa tạo (Trước khi UC-23 thu tiền) -> 200 OK")
    void test8_GetSalesOrderDetail_BeforePayment_Success() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sales/orders/{orderId}", createdOrderIdForPaymentFlow))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(createdOrderIdForPaymentFlow.toString()))
                .andExpect(jsonPath("$.data.orderCode").value("SO-20261103-0001"))
                .andExpect(jsonPath("$.data.orderStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.totalAmount").value(10000000))
                .andReturn();

        printHttpResult("8. GET /api/v1/sales/orders/" + createdOrderIdForPaymentFlow + " (Chi tiết đơn trước thanh toán)", null, result);
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("9. [Tích hợp UC-21 <-> UC-23 (Nam) & UC-70] Kiểm tra thông tin đơn, cập nhật PARTIALLY_PAID -> PAID và xem lại chi tiết đơn")
    void test9_IntegrationWithNamUc23AndUc70_Success() throws Exception {
        // BƯỚC 1 (UC-23 của Nam): Kiểm tra đơn hàng tồn tại, còn hiệu lực và lấy tổng tiền cần thanh toán (10.000.000đ)
        Optional<SalesOrderPaymentInfoDto> paymentInfoOpt = salesOrderApi.getOrderPaymentInfo(createdOrderIdForPaymentFlow);
        assertThat(paymentInfoOpt).isPresent();
        SalesOrderPaymentInfoDto paymentInfo = paymentInfoOpt.get();
        assertThat(paymentInfo.validForPayment()).isTrue();
        assertThat(paymentInfo.totalAmount()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(paymentInfo.paymentStatus()).isEqualTo("UNPAID");

        System.out.println("\n================================================================================");
        System.out.println(">>> [UC-23 BƯỚC 1] Nam gọi salesOrderApi.getOrderPaymentInfo(" + createdOrderIdForPaymentFlow + "):");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(paymentInfo));

        // BƯỚC 2 (UC-23 của Nam): Khách trả 3 triệu tiền mặt (Verified) + 7 triệu chuyển khoản (Pending)
        // -> Nam tính tiền đã xác minh = 3 triệu < 10 triệu -> Nam gọi salesOrderApi.updatePaymentStatus(..., "PARTIALLY_PAID")
        salesOrderApi.updatePaymentStatus(createdOrderIdForPaymentFlow, "PARTIALLY_PAID");

        MvcResult afterPartialResult = mockMvc.perform(get("/api/v1/sales/orders/{orderId}", createdOrderIdForPaymentFlow))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTIALLY_PAID"))
                .andReturn();

        printHttpResult("9a. GET /api/v1/sales/orders/" + createdOrderIdForPaymentFlow + " (Sau khi UC-23 thu 3tr tiền mặt Verified + 7tr CK Pending)", null, afterPartialResult);

        // BƯỚC 3 (UC-70 Kế toán xác minh khoản chuyển khoản 7 triệu):
        // -> Tổng tiền Verified = 3tr + 7tr = 10 triệu -> Gọi salesOrderApi.updatePaymentStatus(..., "PAID")
        salesOrderApi.updatePaymentStatus(createdOrderIdForPaymentFlow, "PAID");

        MvcResult afterPaidResult = mockMvc.perform(get("/api/v1/sales/orders/{orderId}", createdOrderIdForPaymentFlow))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andReturn();

        printHttpResult("9b. GET /api/v1/sales/orders/" + createdOrderIdForPaymentFlow + " (Sau khi UC-70 xác minh 7tr CK -> PAID)", null, afterPaidResult);
    }

    private void printHttpResult(String stepTitle, String reqJson, MvcResult result) throws Exception {
        System.out.println("\n================================================================================");
        System.out.println("TEST CASE: " + stepTitle);
        System.out.println("HTTP STATUS: " + result.getResponse().getStatus());
        if (reqJson != null) {
            System.out.println("--- REQUEST BODY ---");
            System.out.println(reqJson);
        }
        System.out.println("--- RESPONSE BODY ---");
        String resBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (resBody != null && !resBody.isBlank()) {
            Object jsonObj = objectMapper.readValue(resBody, Object.class);
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObj));
        }
        System.out.println("================================================================================\n");
    }
}

