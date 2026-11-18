package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.sales.internal.entity.CartItem;
import com.aquasuite.ims.module.sales.internal.mapper.CartMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.AddCartItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.MergeCartItemsRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdateCartItemQuantityRequest;
import com.aquasuite.ims.module.sales.internal.repository.CartItemRepository;
import com.aquasuite.ims.module.sales.internal.service.CartService;
import com.aquasuite.ims.module.sales.internal.service.impl.CartServiceImpl;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bộ kiểm thử giả lập Postman End-to-End cho toàn bộ REST API của UC-17 (Quản lý Giỏ hàng - Lâm).
 * Chạy xuyên suốt qua CartController -> CartServiceImpl -> CartMapper -> GlobalExceptionHandler
 * với In-Memory Stateful Store mô phỏng bảng cart_items.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CartPostmanSimulationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    // In-memory stateful storage giả lập bảng cart_items xuyên suốt các request Postman
    private final Map<UUID, CartItem> cartStore = new LinkedHashMap<>();

    private UUID customerUserId;
    private UUID customerId;
    private UUID skuToiletId;
    private UUID skuBasinId;
    private UUID basinCartItemId;

    @BeforeAll
    @SuppressWarnings("unchecked")
    void setupSimulation() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        CartItemRepository cartItemRepository = Mockito.mock(CartItemRepository.class);
        IamApi iamApi = Mockito.mock(IamApi.class);
        CrmApi crmApi = Mockito.mock(CrmApi.class);
        CatalogApi catalogApi = Mockito.mock(CatalogApi.class);
        InventoryApi inventoryApi = Mockito.mock(InventoryApi.class);
        CartMapper cartMapper = Mappers.getMapper(CartMapper.class);

        customerUserId = UUID.fromString("11111111-1111-1111-1111-000000000017");
        customerId = UUID.fromString("22222222-2222-2222-2222-000000000017");
        skuToiletId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000001");
        skuBasinId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000002");

        // 1. Mock IAM & CRM (Khách hàng đang đăng nhập)
        when(iamApi.getCurrentUserId()).thenReturn(customerUserId);
        when(crmApi.getCustomerByUserId(customerUserId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder()
                        .customerId(customerId)
                        .userId(customerUserId)
                        .customerCode("CUS-2026-017")
                        .customerName("Nguyễn Văn Lâm (Customer Online)")
                        .phone("0901234567")
                        .email("lam.customer@aquasuite.com")
                        .status("ACTIVE")
                        .build()
        ));

        // 2. Mock Catalog SKUs
        CatalogSkuSummaryDto toiletSku = CatalogSkuSummaryDto.builder()
                .variantId(skuToiletId)
                .productId(UUID.fromString("dddddddd-0000-0000-0000-000000000001"))
                .sku("TOTO-NEOREST-01")
                .productName("Bồn cầu thông minh TOTO Neorest")
                .variantName("Màu trắng sứ - Tự động xả")
                .unit("Bộ")
                .sellingPrice(new BigDecimal("5000000"))
                .status("ACTIVE")
                .build();

        CatalogSkuSummaryDto basinSku = CatalogSkuSummaryDto.builder()
                .variantId(skuBasinId)
                .productId(UUID.fromString("dddddddd-0000-0000-0000-000000000002"))
                .sku("INAX-LAVABO-02")
                .productName("Chậu rửa đặt bàn INAX")
                .variantName("Men Aqua Ceramic chống bám bẩn")
                .unit("Cái")
                .sellingPrice(new BigDecimal("3000000"))
                .status("ACTIVE")
                .build();

        Map<UUID, CatalogSkuSummaryDto> catalogCatalog = Map.of(
                skuToiletId, toiletSku,
                skuBasinId, basinSku
        );

        when(catalogApi.getSkuSummary(skuToiletId)).thenReturn(Optional.of(toiletSku));
        when(catalogApi.getSkuSummary(skuBasinId)).thenReturn(Optional.of(basinSku));
        when(catalogApi.getSkuSummaries(any(Collection.class))).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            Map<UUID, CatalogSkuSummaryDto> res = new LinkedHashMap<>();
            for (UUID id : ids) {
                if (catalogCatalog.containsKey(id)) {
                    res.put(id, catalogCatalog.get(id));
                }
            }
            return res;
        });

        // 3. Mock Inventory Stock (mỗi SKU tồn khả dụng 10)
        StockSummaryDto toiletStock = StockSummaryDto.builder()
                .variantId(skuToiletId)
                .warehouseCode("MAIN")
                .onHandQty(new BigDecimal("12"))
                .reservedQty(new BigDecimal("2"))
                .availableQty(new BigDecimal("10"))
                .build();

        StockSummaryDto basinStock = StockSummaryDto.builder()
                .variantId(skuBasinId)
                .warehouseCode("MAIN")
                .onHandQty(new BigDecimal("15"))
                .reservedQty(new BigDecimal("5"))
                .availableQty(new BigDecimal("10"))
                .build();

        Map<UUID, StockSummaryDto> stockCatalog = Map.of(
                skuToiletId, toiletStock,
                skuBasinId, basinStock
        );

        when(inventoryApi.getStockSummary(skuToiletId)).thenReturn(Optional.of(toiletStock));
        when(inventoryApi.getStockSummary(skuBasinId)).thenReturn(Optional.of(basinStock));
        when(inventoryApi.getStockSummaries(any(Collection.class))).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            Map<UUID, StockSummaryDto> res = new LinkedHashMap<>();
            for (UUID id : ids) {
                if (stockCatalog.containsKey(id)) {
                    res.put(id, stockCatalog.get(id));
                }
            }
            return res;
        });

        // 4. Mock Stateful CartItemRepository
        when(cartItemRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId)).thenAnswer(inv ->
                new ArrayList<>(cartStore.values())
        );

        when(cartItemRepository.findByCustomerIdAndVariantId(any(UUID.class), any(UUID.class))).thenAnswer(inv -> {
            UUID custId = inv.getArgument(0);
            UUID varId = inv.getArgument(1);
            return cartStore.values().stream()
                    .filter(item -> custId.equals(item.getCustomerId()) && varId.equals(item.getVariantId()))
                    .findFirst();
        });

        when(cartItemRepository.findByCartItemIdAndCustomerId(any(UUID.class), any(UUID.class))).thenAnswer(inv -> {
            UUID itemId = inv.getArgument(0);
            UUID custId = inv.getArgument(1);
            CartItem item = cartStore.get(itemId);
            if (item != null && custId.equals(item.getCustomerId())) {
                return Optional.of(item);
            }
            return Optional.empty();
        });

        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> {
            CartItem item = inv.getArgument(0);
            if (item.getCartItemId() == null) {
                item.setCartItemId(UUID.randomUUID());
            }
            item.setUpdatedAt(OffsetDateTime.now());
            cartStore.put(item.getCartItemId(), item);
            return item;
        });

        doAnswer(inv -> {
            CartItem item = inv.getArgument(0);
            cartStore.remove(item.getCartItemId());
            return null;
        }).when(cartItemRepository).delete(any(CartItem.class));

        doAnswer(inv -> {
            cartStore.clear();
            return null;
        }).when(cartItemRepository).deleteAllByCustomerId(customerId);

        CartService cartService = new CartServiceImpl(
                cartItemRepository,
                cartMapper,
                iamApi,
                crmApi,
                catalogApi,
                inventoryApi
        );

        CartController cartController = new CartController(cartService);

        mockMvc = MockMvcBuilders.standaloneSetup(cartController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("1. [GET /api/v1/sales/cart] Xem giỏ hàng khi mới đăng nhập -> 200 OK, giỏ rỗng")
    void step1_GetInitialEmptyCart() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sales/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.subtotal").value(0))
                .andReturn();

        printHttpResult("1. GET /api/v1/sales/cart (Giỏ hàng trống ban đầu)", null, result);
    }

    @Test
    @Order(2)
    @DisplayName("2. [POST /api/v1/sales/cart/items] Thêm 2 Bồn cầu TOTO Neorest (5tr/bộ) -> 200 OK, subtotal = 10tr")
    void step2_AddFirstVariantToCart() throws Exception {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .variantId(skuToiletId)
                .quantity(new BigDecimal("2"))
                .build();
        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã thêm sản phẩm vào giỏ hàng"))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(10000000.0))
                .andExpect(jsonPath("$.data.items[0].sku").value("TOTO-NEOREST-01"))
                .andReturn();

        printHttpResult("2. POST /api/v1/sales/cart/items (Thêm 2 Bồn cầu TOTO - 10 triệu)", reqJson, result);
    }

    @Test
    @Order(3)
    @DisplayName("3. [POST /api/v1/sales/cart/items] Thêm trùng Bồn cầu TOTO (+1 bộ) -> Cộng dồn quantity = 3, subtotal = 15tr")
    void step3_AddDuplicateVariantAccumulatesQuantity() throws Exception {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .variantId(skuToiletId)
                .quantity(new BigDecimal("1"))
                .build();
        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.totalQuantity").value(3))
                .andExpect(jsonPath("$.data.subtotal").value(15000000.0))
                .andReturn();

        printHttpResult("3. POST /api/v1/sales/cart/items (Thêm trùng SKU -> Tự cộng dồn lên 3 bộ = 15 triệu)", reqJson, result);
    }

    @Test
    @Order(4)
    @DisplayName("4. [POST /api/v1/sales/cart/items] Thêm 2 Chậu rửa INAX (3tr/cái) -> 2 mặt hàng, subtotal = 21tr")
    void step4_AddSecondVariantToCart() throws Exception {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .variantId(skuBasinId)
                .quantity(new BigDecimal("2"))
                .build();
        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.totalQuantity").value(5))
                .andExpect(jsonPath("$.data.subtotal").value(21000000.0))
                .andReturn();

        basinCartItemId = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .path("data").path("items").get(1).path("cartItemId").asText()
        );

        printHttpResult("4. POST /api/v1/sales/cart/items (Thêm 2 Chậu rửa INAX -> Tổng 21 triệu)", reqJson, result);
    }

    @Test
    @Order(5)
    @DisplayName("5. [PUT /api/v1/sales/cart/items/{id}] Cập nhật số lượng Chậu rửa INAX từ 2 -> 4 -> subtotal = 27tr")
    void step5_UpdateCartItemQuantity() throws Exception {
        UpdateCartItemQuantityRequest request = UpdateCartItemQuantityRequest.builder()
                .quantity(new BigDecimal("4"))
                .build();
        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(put("/api/v1/sales/cart/items/{cartItemId}", basinCartItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã cập nhật số lượng sản phẩm"))
                .andExpect(jsonPath("$.data.totalQuantity").value(7))
                .andExpect(jsonPath("$.data.subtotal").value(27000000.0))
                .andReturn();

        printHttpResult("5. PUT /api/v1/sales/cart/items/" + basinCartItemId + " (Đổi số lượng Chậu rửa lên 4 -> Tổng 27 triệu)", reqJson, result);
    }

    @Test
    @Order(6)
    @DisplayName("6. [POST /api/v1/sales/cart/items] Đặt vượt tồn kho khả dụng (available = 10) -> 400 Bad Request (SALES_001)")
    void step6_AddExceedingAvailableStock_Returns400() throws Exception {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .variantId(skuToiletId)
                .quantity(new BigDecimal("20"))
                .build();
        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SALES_001"))
                .andReturn();

        printHttpResult("6. POST /api/v1/sales/cart/items (Lỗi đặt vượt tồn kho khả dụng -> 400 SALES_001)", reqJson, result);
    }

    @Test
    @Order(7)
    @DisplayName("7. [POST /api/v1/sales/cart/items] Gửi số lượng = 0 -> 400 Bad Request Validation")
    void step7_InvalidQuantityPayload_Returns400Validation() throws Exception {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .variantId(skuToiletId)
                .quantity(BigDecimal.ZERO)
                .build();
        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andReturn();

        printHttpResult("7. POST /api/v1/sales/cart/items (Validation lỗi số lượng = 0 -> 400)", reqJson, result);
    }

    @Test
    @Order(8)
    @DisplayName("8. [DELETE /api/v1/sales/cart/items/{id}] Xóa dòng Chậu rửa INAX khỏi giỏ -> Còn 1 mặt hàng, subtotal = 15tr")
    void step8_RemoveSingleItemFromCart() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/v1/sales/cart/items/{cartItemId}", basinCartItemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xóa sản phẩm khỏi giỏ hàng"))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.subtotal").value(15000000.0))
                .andReturn();

        printHttpResult("8. DELETE /api/v1/sales/cart/items/" + basinCartItemId + " (Xóa 1 dòng sản phẩm -> Còn 15 triệu)", null, result);
    }

    @Test
    @Order(9)
    @DisplayName("9. [DELETE /api/v1/sales/cart] Làm trống toàn bộ giỏ hàng -> 200 OK, giỏ rỗng")
    void step9_ClearEntireCart() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/v1/sales/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã làm trống giỏ hàng"))
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.subtotal").value(0))
                .andReturn();

        printHttpResult("9. DELETE /api/v1/sales/cart (Làm trống toàn bộ giỏ hàng)", null, result);
    }

    @Test
    @Order(10)
    @DisplayName("10. [POST /api/v1/sales/cart/items/merge] Gộp Guest Cart sau đăng nhập -> 200 OK")
    void step10_MergeGuestItems_Success() throws Exception {
        MergeCartItemsRequest request = MergeCartItemsRequest.builder()
                .items(List.of(
                        AddCartItemRequest.builder().variantId(skuToiletId).quantity(new BigDecimal("1")).build(),
                        AddCartItemRequest.builder().variantId(skuBasinId).quantity(new BigDecimal("2")).build()
                ))
                .build();
        String reqJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/cart/items/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andReturn();

        printHttpResult("10. POST /api/v1/sales/cart/items/merge (Gộp Guest Cart)", reqJson, result);
    }

    private void printHttpResult(String stepTitle, String reqJson, MvcResult result) throws Exception {
        System.out.println("\n================================================================================");
        System.out.println("POSTMAN STEP: " + stepTitle);
        System.out.println("HTTP STATUS : " + result.getResponse().getStatus());
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

