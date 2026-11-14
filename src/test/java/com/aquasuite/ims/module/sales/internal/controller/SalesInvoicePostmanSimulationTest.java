package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.finance.api.CreditControlApi;
import com.aquasuite.ims.module.finance.api.OrderReceivableApi;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationDecision;
import com.aquasuite.ims.module.finance.api.dto.OrderReceivableView;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.sales.api.SalesInvoiceApi;
import com.aquasuite.ims.module.sales.api.event.SalesInvoiceIssuedEvent;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.Quotation;
import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoiceItem;
import com.aquasuite.ims.module.sales.internal.enums.FulfillmentMethod;
import com.aquasuite.ims.module.sales.internal.enums.OrderChannel;
import com.aquasuite.ims.module.sales.internal.enums.OrderType;
import com.aquasuite.ims.module.sales.internal.enums.PaymentMethod;
import com.aquasuite.ims.module.sales.internal.enums.QuotationStatus;
import com.aquasuite.ims.module.sales.internal.mapper.SalesOrderMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.ConvertQuotationToOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateDraftInvoiceRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateOrderItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.InitialInvoiceBatchRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdateSalesInvoiceRequest;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderModificationRequestRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderPaymentMilestoneRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationPaymentMilestoneRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import com.aquasuite.ims.module.sales.internal.service.OrderStockReservationService;
import com.aquasuite.ims.module.sales.internal.service.SalesInvoiceService;
import com.aquasuite.ims.module.sales.internal.service.SalesOrderService;
import com.aquasuite.ims.module.sales.internal.service.helper.OrderCodeGenerator;
import com.aquasuite.ims.module.sales.internal.service.impl.SalesInvoiceApiImpl;
import com.aquasuite.ims.module.sales.internal.service.impl.SalesInvoiceServiceImpl;
import com.aquasuite.ims.module.sales.internal.service.impl.SalesOrderServiceImpl;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử mô phỏng Postman End-to-End cho luồng Hóa đơn bán hàng (DRAFT ->
 * ISSUED / CANCELLED)
 * trên cả đơn bán lẻ (STORE) và đơn công trình nhiều đợt (PROJECT), UC-26 lock
 * & Payment Floor.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SalesInvoicePostmanSimulationTest {

        private MockMvc mockMvc;
        private ObjectMapper objectMapper;
        private SalesInvoiceApi salesInvoiceApi;
        private InventoryApi inventoryApi;
        private OrderReceivableApi orderReceivableApi;
        private OrderModificationRequestRepository modificationRequestRepository;

        private final Map<UUID, Order> orderStore = new LinkedHashMap<>();
        private final Map<UUID, List<OrderItem>> orderItemStore = new LinkedHashMap<>();
        private final Map<UUID, SalesInvoice> invoiceStore = new LinkedHashMap<>();
        private final Map<UUID, List<SalesInvoiceItem>> invoiceItemStore = new LinkedHashMap<>();
        private final Map<UUID, Quotation> quotationStore = new LinkedHashMap<>();
        private final Map<UUID, List<QuotationItem>> quotationItemStore = new LinkedHashMap<>();
        private final List<Object> publishedEvents = new ArrayList<>();
        private final AtomicInteger orderSeq = new AtomicInteger(1);

        private UUID customerId;
        private UUID staffId;
        private UUID sku1Id;
        private UUID sku2Id;
        private UUID sku3Id;
        private UUID quotationId;

        private UUID retailOrderId;
        private UUID retailInvoiceId;
        private UUID projectOrderId;
        private UUID projectBatch1Id;
        private UUID projectBatch2Id;
        private UUID projectBatch3Id;

        @BeforeAll
        @SuppressWarnings("unchecked")
        void setupSimulation() {
                objectMapper = new ObjectMapper()
                                .registerModule(new JavaTimeModule())
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

                OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
                OrderItemRepository orderItemRepository = Mockito.mock(OrderItemRepository.class);
                SalesInvoiceRepository salesInvoiceRepository = Mockito.mock(SalesInvoiceRepository.class);
                SalesInvoiceItemRepository salesInvoiceItemRepository = Mockito.mock(SalesInvoiceItemRepository.class);
                QuotationRepository quotationRepository = Mockito.mock(QuotationRepository.class);
                QuotationItemRepository quotationItemRepository = Mockito.mock(QuotationItemRepository.class);
                QuotationPaymentMilestoneRepository qMilestoneRepo = Mockito
                                .mock(QuotationPaymentMilestoneRepository.class);
                OrderPaymentMilestoneRepository oMilestoneRepo = Mockito.mock(OrderPaymentMilestoneRepository.class);
                modificationRequestRepository = Mockito.mock(OrderModificationRequestRepository.class);

                CatalogApi catalogApi = Mockito.mock(CatalogApi.class);
                inventoryApi = Mockito.mock(InventoryApi.class);
                orderReceivableApi = Mockito.mock(OrderReceivableApi.class);
                CreditControlApi creditControlApi = Mockito.mock(CreditControlApi.class);
                CrmApi crmApi = Mockito.mock(CrmApi.class);
                IamApi iamApi = Mockito.mock(IamApi.class);
                OrderCodeGenerator orderCodeGenerator = Mockito.mock(OrderCodeGenerator.class);
                OrderStockReservationService reservationService = Mockito.mock(OrderStockReservationService.class);
                ApplicationEventPublisher eventPublisher = publishedEvents::add;

                customerId = UUID.fromString("11111111-2222-3333-4444-555555555511");
                staffId = UUID.fromString("99999999-8888-7777-6666-555555555511");
                sku1Id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000011");
                sku2Id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000012");
                sku3Id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000013");
                quotationId = UUID.fromString("bbbbbbbb-2222-3333-4444-555555555511");

                // Mock Master Data
                when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(CustomerSummaryDto.builder()
                                .customerId(customerId)
                                .customerCode("CUS-PROJECT-01")
                                .customerName("Công ty Xây dựng Bách Khoa")
                                .phone("0912345678")
                                .status("ACTIVE")
                                .build()));
                when(iamApi.getCurrentUserId()).thenReturn(staffId);
                when(iamApi.getUserById(staffId)).thenReturn(new UserSummaryDto(
                                staffId, "lam_sales", "lam@aquasuite.vn", "Hoàng Tùng Lâm", "SALES_STAFF", "ACTIVE"));
                when(orderCodeGenerator.generateOrderCode()).thenAnswer(
                                inv -> String.format("SO-20261114-%04d", orderSeq.getAndIncrement()));
                when(creditControlApi.authorize(any())).thenReturn(new CreditAuthorizationDecision(
                                true, null, new BigDecimal("100000000")));

                CatalogSkuSummaryDto sku1 = CatalogSkuSummaryDto.builder()
                                .variantId(sku1Id).sku("TOTO-C884").productName("Bồn cầu TOTO 1 khối")
                                .variantName("Trắng").sellingPrice(new BigDecimal("5000000")).status("ACTIVE").build();
                CatalogSkuSummaryDto sku2 = CatalogSkuSummaryDto.builder()
                                .variantId(sku2Id).sku("INAX-L284").productName("Lavabo Inax treo tường")
                                .variantName("Tiêu chuẩn").sellingPrice(new BigDecimal("2000000")).status("ACTIVE")
                                .build();
                CatalogSkuSummaryDto sku3 = CatalogSkuSummaryDto.builder()
                                .variantId(sku3Id).sku("GROHE-V01").productName("Vòi chậu Grohe nóng lạnh")
                                .variantName("Chrome").sellingPrice(new BigDecimal("3500000")).status("ACTIVE").build();

                when(catalogApi.getSkuSummary(sku1Id)).thenReturn(Optional.of(sku1));
                when(catalogApi.getSkuSummary(sku2Id)).thenReturn(Optional.of(sku2));
                when(catalogApi.getSkuSummary(sku3Id)).thenReturn(Optional.of(sku3));

                when(inventoryApi.checkAvailability(any(UUID.class), any(BigDecimal.class))).thenReturn(true);
                when(inventoryApi.getStockSummary(any(UUID.class))).thenAnswer(inv -> Optional.of(
                                new StockSummaryDto(inv.getArgument(0), "SKU", new BigDecimal("100"),
                                                new BigDecimal("50"), new BigDecimal("50"), BigDecimal.ZERO)));

                // Stateful Order & OrderItem stores
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
                        Order o = inv.getArgument(0);
                        orderStore.put(o.getOrderId(), o);
                        return o;
                });
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                        Order o = inv.getArgument(0);
                        orderStore.put(o.getOrderId(), o);
                        return o;
                });
                when(orderRepository.findById(any(UUID.class)))
                                .thenAnswer(inv -> Optional.ofNullable(orderStore.get(inv.getArgument(0))));
                when(orderRepository.findByIdForUpdate(any(UUID.class)))
                                .thenAnswer(inv -> Optional.ofNullable(orderStore.get(inv.getArgument(0))));
                when(orderRepository.findByQuotationId(any(UUID.class))).thenAnswer(inv -> {
                        UUID qId = inv.getArgument(0);
                        return orderStore.values().stream().filter(o -> qId.equals(o.getQuotationId())).findFirst();
                });

                when(orderItemRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> {
                        List<OrderItem> items = inv.getArgument(0);
                        for (OrderItem oi : items) {
                                orderItemStore.computeIfAbsent(oi.getOrderId(), k -> new ArrayList<>())
                                                .removeIf(existing -> existing.getOrderItemId()
                                                                .equals(oi.getOrderItemId()));
                                orderItemStore.get(oi.getOrderId()).add(oi);
                        }
                        return items;
                });
                when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> {
                        List<OrderItem> items = inv.getArgument(0);
                        for (OrderItem oi : items) {
                                orderItemStore.computeIfAbsent(oi.getOrderId(), k -> new ArrayList<>())
                                                .removeIf(existing -> existing.getOrderItemId()
                                                                .equals(oi.getOrderItemId()));
                                orderItemStore.get(oi.getOrderId()).add(oi);
                        }
                        return items;
                });
                when(orderItemRepository.saveAndFlush(any(OrderItem.class))).thenAnswer(inv -> {
                        OrderItem oi = inv.getArgument(0);
                        orderItemStore.computeIfAbsent(oi.getOrderId(), k -> new ArrayList<>())
                                        .removeIf(existing -> existing.getOrderItemId().equals(oi.getOrderItemId()));
                        orderItemStore.get(oi.getOrderId()).add(oi);
                        return oi;
                });
                when(orderItemRepository.findByOrderId(any(UUID.class)))
                                .thenAnswer(inv -> new ArrayList<>(
                                                orderItemStore.getOrDefault(inv.getArgument(0), List.of())));
                Mockito.doAnswer(inv -> {
                        OrderItem target = inv.getArgument(0);
                        List<OrderItem> list = orderItemStore.get(target.getOrderId());
                        if (list != null) {
                                list.removeIf(x -> x.getOrderItemId().equals(target.getOrderItemId()));
                        }
                        return null;
                }).when(orderItemRepository).delete(any(OrderItem.class));

                // Stateful SalesInvoice & SalesInvoiceItem stores
                when(salesInvoiceRepository.saveAndFlush(any(SalesInvoice.class))).thenAnswer(inv -> {
                        SalesInvoice si = inv.getArgument(0);
                        if (si.getBatchNo() == null)
                                si.setBatchNo(1);
                        invoiceStore.put(si.getInvoiceId(), si);
                        return si;
                });
                when(salesInvoiceRepository.findById(any(UUID.class)))
                                .thenAnswer(inv -> Optional.ofNullable(invoiceStore.get(inv.getArgument(0))));
                when(salesInvoiceRepository.findByInvoiceIdForUpdate(any(UUID.class)))
                                .thenAnswer(inv -> Optional.ofNullable(invoiceStore.get(inv.getArgument(0))));
                when(salesInvoiceRepository.findByOrderIdAndInvoiceType(any(UUID.class), any(String.class)))
                                .thenAnswer(inv -> {
                                        UUID oId = inv.getArgument(0);
                                        String type = inv.getArgument(1);
                                        return invoiceStore.values().stream()
                                                        .filter(si -> oId.equals(si.getOrderId())
                                                                        && type.equalsIgnoreCase(si.getInvoiceType()))
                                                        .findFirst();
                                });
                when(salesInvoiceRepository.findByOrderIdOrderByBatchNoAscCreatedAtAsc(any(UUID.class)))
                                .thenAnswer(inv -> {
                                        UUID oId = inv.getArgument(0);
                                        return invoiceStore.values().stream()
                                                        .filter(si -> oId.equals(si.getOrderId()))
                                                        .sorted(Comparator.comparing(
                                                                        si -> si.getBatchNo() != null ? si.getBatchNo()
                                                                                        : 1))
                                                        .toList();
                                });
                when(salesInvoiceRepository.findByOrderIdAndStatusOrderByBatchNoAscCreatedAtAsc(any(UUID.class),
                                any(String.class)))
                                .thenAnswer(inv -> {
                                        UUID oId = inv.getArgument(0);
                                        String st = inv.getArgument(1);
                                        return invoiceStore.values().stream()
                                                        .filter(si -> oId.equals(si.getOrderId())
                                                                        && st.equalsIgnoreCase(si.getStatus()))
                                                        .sorted(Comparator.comparing(
                                                                        si -> si.getBatchNo() != null ? si.getBatchNo()
                                                                                        : 1))
                                                        .toList();
                                });
                when(salesInvoiceRepository.countByOrderIdAndStatusNot(any(UUID.class), any(String.class)))
                                .thenAnswer(inv -> {
                                        UUID oId = inv.getArgument(0);
                                        String excluded = inv.getArgument(1);
                                        return invoiceStore.values().stream()
                                                        .filter(si -> oId.equals(si.getOrderId())
                                                                        && !excluded.equalsIgnoreCase(si.getStatus()))
                                                        .count();
                                });

                when(salesInvoiceItemRepository.saveAll(anyList())).thenAnswer(inv -> {
                        List<SalesInvoiceItem> items = inv.getArgument(0);
                        for (SalesInvoiceItem it : items) {
                                invoiceItemStore.computeIfAbsent(it.getInvoiceId(), k -> new ArrayList<>())
                                                .removeIf(x -> x.getInvoiceItemId().equals(it.getInvoiceItemId()));
                                invoiceItemStore.get(it.getInvoiceId()).add(it);
                        }
                        return items;
                });
                when(salesInvoiceItemRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> {
                        List<SalesInvoiceItem> items = inv.getArgument(0);
                        for (SalesInvoiceItem it : items) {
                                invoiceItemStore.computeIfAbsent(it.getInvoiceId(), k -> new ArrayList<>())
                                                .removeIf(x -> x.getInvoiceItemId().equals(it.getInvoiceItemId()));
                                invoiceItemStore.get(it.getInvoiceId()).add(it);
                        }
                        return items;
                });
                when(salesInvoiceItemRepository.findByInvoiceIdOrderByInvoiceItemIdAsc(any(UUID.class)))
                                .thenAnswer(inv -> new ArrayList<>(
                                                invoiceItemStore.getOrDefault(inv.getArgument(0), List.of())));
                when(salesInvoiceItemRepository.findByInvoiceIdIn(any(Collection.class)))
                                .thenAnswer(inv -> {
                                        Collection<UUID> ids = inv.getArgument(0);
                                        List<SalesInvoiceItem> out = new ArrayList<>();
                                        for (UUID id : ids) {
                                                out.addAll(invoiceItemStore.getOrDefault(id, List.of()));
                                        }
                                        return out;
                                });
                Mockito.doAnswer(inv -> {
                        UUID invId = inv.getArgument(0);
                        invoiceItemStore.remove(invId);
                        return null;
                }).when(salesInvoiceItemRepository).deleteByInvoiceId(any(UUID.class));
                Mockito.doAnswer(inv -> {
                        Iterable<SalesInvoiceItem> toDelete = inv.getArgument(0);
                        for (SalesInvoiceItem d : toDelete) {
                                List<SalesInvoiceItem> list = invoiceItemStore.get(d.getInvoiceId());
                                if (list != null) {
                                        list.removeIf(x -> x.getInvoiceItemId().equals(d.getInvoiceItemId()));
                                }
                        }
                        return null;
                }).when(salesInvoiceItemRepository).deleteAll(anyList());
                when(salesInvoiceItemRepository.existsByOrderItemIdAndInvoiceIdIn(any(UUID.class),
                                any(Collection.class)))
                                .thenAnswer(inv -> {
                                        UUID oiId = inv.getArgument(0);
                                        Collection<UUID> invIds = inv.getArgument(1);
                                        for (UUID id : invIds) {
                                                if (invoiceItemStore.getOrDefault(id, List.of()).stream()
                                                                .anyMatch(x -> oiId.equals(x.getOrderItemId()))) {
                                                        return true;
                                                }
                                        }
                                        return false;
                                });

                // Quotation sample for PROJECT order conversion
                Quotation quotation = Quotation.builder()
                                .quotationId(quotationId)
                                .quotationCode("BG-202611-0099")
                                .revisionNo(1)
                                .customerId(customerId)
                                .projectName("Chung cư Aqua Riverside")
                                .deliverySchedule("Giao chia 2 đợt")
                                .vatRate(new BigDecimal("10.00"))
                                .shippingFee(BigDecimal.ZERO)
                                .creditTermDays(15)
                                .status(QuotationStatus.APPROVED)
                                .validUntil(LocalDate.now().plusDays(15))
                                .totalAmount(new BigDecimal("66000000"))
                                .createdAt(OffsetDateTime.now())
                                .updatedAt(OffsetDateTime.now())
                                .build();
                quotationStore.put(quotationId, quotation);
                quotationItemStore.put(quotationId, List.of(
                                QuotationItem.builder()
                                                .quotationItemId(UUID.randomUUID()).quotationId(quotationId)
                                                .variantId(sku1Id).sku("TOTO-C884").unit("chiếc")
                                                .quantity(new BigDecimal("10")).unitPrice(new BigDecimal("5000000"))
                                                .discountRate(BigDecimal.ZERO)
                                                .sortOrder(1).build(),
                                QuotationItem.builder()
                                                .quotationItemId(UUID.randomUUID()).quotationId(quotationId)
                                                .variantId(sku2Id).sku("INAX-L284").unit("chiếc")
                                                .quantity(new BigDecimal("5")).unitPrice(new BigDecimal("2000000"))
                                                .discountRate(BigDecimal.ZERO)
                                                .sortOrder(2).build()));
                when(quotationRepository.findById(quotationId))
                                .thenAnswer(inv -> Optional.ofNullable(quotationStore.get(quotationId)));
                when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId))
                                .thenAnswer(inv -> quotationItemStore.get(quotationId));
                when(qMilestoneRepo.findByQuotationIdOrderBySortOrderAsc(quotationId)).thenReturn(List.of());

                when(modificationRequestRepository.existsByOrderIdAndRequestStatusIn(any(UUID.class), anyList()))
                                .thenReturn(false);
                when(orderReceivableApi.getOrderReceivable(any(UUID.class))).thenReturn(Optional.empty());

                SalesOrderService salesOrderService = new SalesOrderServiceImpl(
                                orderRepository, orderItemRepository, salesInvoiceRepository,
                                salesInvoiceItemRepository,
                                creditControlApi, catalogApi, inventoryApi, crmApi, iamApi,
                                Mappers.getMapper(SalesOrderMapper.class), orderCodeGenerator,
                                quotationRepository, quotationItemRepository, qMilestoneRepo, oMilestoneRepo,
                                reservationService, orderReceivableApi, eventPublisher);

                SalesInvoiceService salesInvoiceService = new SalesInvoiceServiceImpl(
                                salesInvoiceRepository, salesInvoiceItemRepository, orderRepository,
                                orderItemRepository,
                                modificationRequestRepository, catalogApi, inventoryApi, orderReceivableApi,
                                creditControlApi, crmApi, iamApi, eventPublisher);

                salesInvoiceApi = new SalesInvoiceApiImpl(salesInvoiceRepository, salesInvoiceItemRepository,
                                orderRepository);

                mockMvc = MockMvcBuilders
                                .standaloneSetup(new SalesOrderController(salesOrderService),
                                                new SalesInvoiceController(salesInvoiceService))
                                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .build();
        }

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("[POSTMAN-INV-01] RETAIL Order -> Auto initializes 1 STORE DRAFT Invoice (editable)")
        void step1_createRetailOrderCreatesStoreDraftInvoice() throws Exception {
                CreateSalesOrderRequest req = CreateSalesOrderRequest.builder()
                                .customerId(customerId)
                                .orderChannel(OrderChannel.POS)
                                .orderType(OrderType.RETAIL)
                                .fulfillmentMethod(FulfillmentMethod.IN_STORE_PICKUP)
                                .paymentMethod(PaymentMethod.CASH)
                                .discountAmount(new BigDecimal("500000"))
                                .items(List.of(CreateOrderItemRequest.builder().variantId(sku1Id)
                                                .quantity(new BigDecimal("2")).build()))
                                .build();

                MvcResult createRes = mockMvc.perform(post("/api/v1/sales/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.totalAmount").value(9500000))
                                .andExpect(jsonPath("$.data.totalInvoiceCount").value(1))
                                .andExpect(jsonPath("$.data.draftInvoiceCount").value(1))
                                .andExpect(jsonPath("$.data.issuedInvoiceCount").value(0))
                                .andReturn();

                JsonNode orderData = objectMapper
                                .readTree(createRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                                .get("data");
                retailOrderId = UUID.fromString(orderData.get("orderId").asText());
                retailInvoiceId = UUID.fromString(orderData.get("storeInvoiceId").asText());

                mockMvc.perform(get("/api/v1/sales/orders/{orderId}/invoices", retailOrderId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data[0].invoiceId").value(retailInvoiceId.toString()))
                                .andExpect(jsonPath("$.data[0].invoiceType").value("STORE"))
                                .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                                .andExpect(jsonPath("$.data[0].editable").value(true))
                                .andExpect(jsonPath("$.data[0].totalAmount").value(9500000));
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("[POSTMAN-INV-02] Edit RETAIL DRAFT Invoice -> syncs Order total & Inventory reservation")
        void step2_editRetailDraftInvoiceSyncsOrderAndInventory() throws Exception {
                // Sửa SKU-1 từ 2 -> 3 (15M) + thêm SKU-2 slg 1 (2M), chiết khấu hóa đơn = 1M =>
                // 16M
                UpdateSalesInvoiceRequest updateReq = UpdateSalesInvoiceRequest.builder()
                                .batchName("Hóa đơn bán lẻ (Đã chỉnh)")
                                .note("Khách đổi tăng số lượng TOTO lên 3 và lấy thêm Lavabo")
                                .discountAmount(new BigDecimal("1000000"))
                                .items(List.of(
                                                UpdateSalesInvoiceRequest.UpdateInvoiceItemInput.builder()
                                                                .variantId(sku1Id).quantity(new BigDecimal("3"))
                                                                .build(),
                                                UpdateSalesInvoiceRequest.UpdateInvoiceItemInput.builder()
                                                                .variantId(sku2Id).quantity(new BigDecimal("1"))
                                                                .build()))
                                .build();

                mockMvc.perform(put("/api/v1/sales/invoices/{invoiceId}", retailInvoiceId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                                .andExpect(jsonPath("$.data.subtotal").value(17000000))
                                .andExpect(jsonPath("$.data.totalAmount").value(16000000))
                                .andExpect(jsonPath("$.data.items.length()").value(2));

                // Kiểm tra OrderDetail tự động cập nhật tổng = 16,000,000
                mockMvc.perform(get("/api/v1/sales/orders/{orderId}", retailOrderId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.totalAmount").value(16000000))
                                .andExpect(jsonPath("$.data.items.length()").value(2));

                verify(inventoryApi, atLeastOnce()).reserveStock(sku1Id, new BigDecimal("1"));
                verify(inventoryApi, atLeastOnce()).reserveStock(sku2Id, new BigDecimal("1"));

                // Chặn tạo thêm batch thứ 2 cho đơn RETAIL (tránh vi phạm V8 STORE unique)
                CreateDraftInvoiceRequest secondRetailBatch = CreateDraftInvoiceRequest.builder()
                                .batchName("Đợt 2")
                                .items(List.of(CreateDraftInvoiceRequest.InvoiceLineInput.builder().variantId(sku3Id)
                                                .quantity(BigDecimal.ONE).build()))
                                .build();
                mockMvc.perform(post("/api/v1/sales/orders/{orderId}/invoices", retailOrderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(secondRetailBatch)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("SALES_024"));
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("[POSTMAN-INV-03] Guardrail -> Blocks reducing DRAFT total below verifiedPaid & deleting only batch")
        void step3_guardrailsVerifiedPaidAndOnlyBatch() throws Exception {
                // Khách đã cọc xác minh 10M -> không thể giảm tổng đơn xuống 2M
                when(orderReceivableApi.getOrderReceivable(retailOrderId)).thenReturn(Optional.of(
                                new OrderReceivableView(retailOrderId, new BigDecimal("16000000"),
                                                new BigDecimal("10000000"), BigDecimal.ZERO, new BigDecimal("6000000"),
                                                "PARTIALLY_PAID", BigDecimal.ZERO, true)));

                UpdateSalesInvoiceRequest dropBelowPaid = UpdateSalesInvoiceRequest.builder()
                                .items(List.of(UpdateSalesInvoiceRequest.UpdateInvoiceItemInput.builder()
                                                .variantId(sku2Id).quantity(BigDecimal.ONE).build()))
                                .discountAmount(BigDecimal.ZERO)
                                .build();

                mockMvc.perform(put("/api/v1/sales/invoices/{invoiceId}", retailInvoiceId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dropBelowPaid)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("SALES_023"));

                when(orderReceivableApi.getOrderReceivable(retailOrderId)).thenReturn(Optional.empty());

                // Không thể xóa hóa đơn duy nhất của đơn hàng
                mockMvc.perform(delete("/api/v1/sales/invoices/{invoiceId}", retailInvoiceId))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("SALES_025"));
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("[POSTMAN-INV-04] Issue RETAIL STORE Invoice -> locks ISSUED, emits event, rejects further edits")
        void step4_issueRetailInvoiceLocksAndPublishesEvent() throws Exception {
                mockMvc.perform(post("/api/v1/sales/invoices/{invoiceId}/issue", retailInvoiceId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                                .andExpect(jsonPath("$.data.editable").value(false))
                                .andExpect(jsonPath("$.data.issuedBy").value(staffId.toString()))
                                .andExpect(jsonPath("$.data.dueDate").isNotEmpty());

                assertThat(publishedEvents.stream().filter(SalesInvoiceIssuedEvent.class::isInstance).toList())
                                .hasSize(1);
                assertThat(salesInvoiceApi.areAllActiveInvoicesIssued(retailOrderId)).isTrue();

                // Chỉnh sửa lại hóa đơn đã ISSUED phải bị từ chối 409 SALES_020
                UpdateSalesInvoiceRequest retryEdit = UpdateSalesInvoiceRequest.builder()
                                .items(List.of(UpdateSalesInvoiceRequest.UpdateInvoiceItemInput.builder()
                                                .variantId(sku1Id).quantity(BigDecimal.ONE).build()))
                                .build();
                mockMvc.perform(put("/api/v1/sales/invoices/{invoiceId}", retailInvoiceId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(retryEdit)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("SALES_020"));
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        @DisplayName("[POSTMAN-INV-05] PROJECT Conversion -> Splits Quotation into 2 PROJECT DRAFT Invoices & validates bad splits")
        void step5_projectQuotationConversionWithMultipleDraftBatches() throws Exception {
                // Thử chia sai tổng số lượng (9 instead of 10) -> phải báo 400 SALES_024
                ConvertQuotationToOrderRequest badConvert = ConvertQuotationToOrderRequest.builder()
                                .batches(List.of(
                                                InitialInvoiceBatchRequest.builder()
                                                                .batchName("Đợt 1")
                                                                .items(List.of(InitialInvoiceBatchRequest.BatchItemRequest
                                                                                .builder()
                                                                                .variantId(sku1Id)
                                                                                .quantity(new BigDecimal("9")).build()))
                                                                .build()))
                                .build();
                mockMvc.perform(post("/api/v1/sales/orders/from-quotation/{quotationId}", quotationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(badConvert)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("SALES_024"));

                // Chia đúng 2 đợt: Đợt 1 (6 TOTO), Đợt 2 (4 TOTO + 5 INAX)
                ConvertQuotationToOrderRequest validConvert = ConvertQuotationToOrderRequest.builder()
                                .batches(List.of(
                                                InitialInvoiceBatchRequest.builder()
                                                                .batchName("Đợt 1 - Phần thô")
                                                                .items(List.of(InitialInvoiceBatchRequest.BatchItemRequest
                                                                                .builder()
                                                                                .variantId(sku1Id)
                                                                                .quantity(new BigDecimal("6")).build()))
                                                                .build(),
                                                InitialInvoiceBatchRequest.builder()
                                                                .batchName("Đợt 2 - Hoàn thiện")
                                                                .items(List.of(
                                                                                InitialInvoiceBatchRequest.BatchItemRequest
                                                                                                .builder()
                                                                                                .variantId(sku1Id)
                                                                                                .quantity(new BigDecimal(
                                                                                                                "4"))
                                                                                                .build(),
                                                                                InitialInvoiceBatchRequest.BatchItemRequest
                                                                                                .builder()
                                                                                                .variantId(sku2Id)
                                                                                                .quantity(new BigDecimal(
                                                                                                                "5"))
                                                                                                .build()))
                                                                .build()))
                                .build();

                MvcResult convertRes = mockMvc
                                .perform(post("/api/v1/sales/orders/from-quotation/{quotationId}", quotationId)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(validConvert)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.orderType").value("PROJECT"))
                                .andExpect(jsonPath("$.data.totalInvoiceCount").value(2))
                                .andExpect(jsonPath("$.data.draftInvoiceCount").value(2))
                                .andExpect(jsonPath("$.data.issuedInvoiceCount").value(0))
                                .andReturn();

                projectOrderId = UUID.fromString(objectMapper.readTree(
                                convertRes.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data")
                                .get("orderId").asText());

                MvcResult listRes = mockMvc.perform(get("/api/v1/sales/orders/{orderId}/invoices", projectOrderId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.length()").value(2))
                                .andExpect(jsonPath("$.data[0].invoiceType").value("PROJECT"))
                                .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                                .andExpect(jsonPath("$.data[0].totalAmount").value(33000000)) // 30M + 10% VAT
                                .andExpect(jsonPath("$.data[1].invoiceType").value("PROJECT"))
                                .andExpect(jsonPath("$.data[1].totalAmount").value(33000000)) // (20M + 10M) + 10% VAT
                                .andReturn();

                JsonNode arr = objectMapper.readTree(listRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                                .get("data");
                projectBatch1Id = UUID.fromString(arr.get(0).get("invoiceId").asText());
                projectBatch2Id = UUID.fromString(arr.get(1).get("invoiceId").asText());
        }

        @Test
        @org.junit.jupiter.api.Order(6)
        @DisplayName("[POSTMAN-INV-06] Split Batch 3 from Batch 2, UC-26 Lock Blocks Edits, Cancel Batch 3, Issue & Complete")
        void step6_splitBatchUc26LockCancelIssueAndFulfillmentContract() throws Exception {
                // 1. Tách 2 TOTO từ Đợt 2 sang Đợt 3
                CreateDraftInvoiceRequest splitReq = CreateDraftInvoiceRequest.builder()
                                .batchName("Đợt 3 - Bổ sung")
                                .splitFromInvoiceId(projectBatch2Id)
                                .items(List.of(CreateDraftInvoiceRequest.InvoiceLineInput.builder()
                                                .variantId(sku1Id).quantity(new BigDecimal("2")).build()))
                                .build();

                MvcResult splitRes = mockMvc.perform(post("/api/v1/sales/orders/{orderId}/invoices", projectOrderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(splitReq)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.batchNo").value(3))
                                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                                .andExpect(jsonPath("$.data.totalAmount").value(11000000)) // 10M + 10% VAT
                                .andReturn();

                projectBatch3Id = UUID.fromString(objectMapper.readTree(
                                splitRes.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data")
                                .get("invoiceId").asText());

                // Đợt 2 giảm còn 2 TOTO + 5 INAX = 20M + 10% VAT = 22M
                mockMvc.perform(get("/api/v1/sales/invoices/{invoiceId}", projectBatch2Id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.totalAmount").value(22000000));

                // 2. Mô phỏng UC-26 (Hào) đang có yêu cầu PENDING_REVIEW -> khóa toàn bộ sửa /
                // phát hành / hủy
                when(modificationRequestRepository.existsByOrderIdAndRequestStatusIn(Mockito.eq(projectOrderId),
                                anyList()))
                                .thenReturn(true);

                mockMvc.perform(get("/api/v1/sales/invoices/{invoiceId}", projectBatch3Id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.editable").value(false))
                                .andExpect(jsonPath("$.data.lockedByModificationRequest").value(true));

                mockMvc.perform(post("/api/v1/sales/invoices/{invoiceId}/issue", projectBatch1Id))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("SALES_022"));

                // Mở khóa UC-26 sau khi duyệt/hủy
                when(modificationRequestRepository.existsByOrderIdAndRequestStatusIn(Mockito.eq(projectOrderId),
                                anyList()))
                                .thenReturn(false);

                // 3. Hủy Đợt 3 nháp
                mockMvc.perform(delete("/api/v1/sales/invoices/{invoiceId}", projectBatch3Id))
                                .andExpect(status().isOk());

                // 4. Phát hành Đợt 1 -> Fulfillment chỉ nhìn thấy 1 hóa đơn ISSUED, toàn đơn
                // chưa ISSUED hết
                mockMvc.perform(post("/api/v1/sales/invoices/{invoiceId}/issue", projectBatch1Id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                                .andExpect(jsonPath("$.data.creditTermDays").value(15));

                assertThat(salesInvoiceApi.getIssuedInvoicesByOrderId(projectOrderId)).hasSize(1);
                assertThat(salesInvoiceApi.areAllActiveInvoicesIssued(projectOrderId)).isFalse();

                // Phát hành Đợt 2 -> toàn bộ hóa đơn hiệu lực của đơn công trình đã ISSUED
                mockMvc.perform(post("/api/v1/sales/invoices/{invoiceId}/issue", projectBatch2Id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.status").value("ISSUED"));

                assertThat(salesInvoiceApi.getIssuedInvoicesByOrderId(projectOrderId)).hasSize(2);
                assertThat(salesInvoiceApi.areAllActiveInvoicesIssued(projectOrderId)).isTrue();

                // Khi đơn đã PAID và toàn bộ hóa đơn ISSUED đã xuất kho -> tự hoàn tất đơn
                Order projOrder = orderStore.get(projectOrderId);
                projOrder.setPaymentStatus("PAID");
                salesInvoiceApi.completeOrderIfEligibleAfterDispatch(projectOrderId);
                assertThat(orderStore.get(projectOrderId).getOrderStatus()).isEqualTo("COMPLETED");
        }
}
