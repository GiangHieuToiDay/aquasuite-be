package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.sales.internal.enums.FulfillmentMethod;
import com.aquasuite.ims.module.sales.internal.enums.PaymentMethod;
import com.aquasuite.ims.module.sales.internal.payload.request.CancelOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateOrderItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdatePreDispatchOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderSummaryResponse;
import com.aquasuite.ims.module.sales.internal.service.SalesOrderService;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private SalesOrderService salesOrderService;

    @InjectMocks
    private SalesOrderController salesOrderController;

    private UUID sampleOrderId;
    private SalesOrderDetailResponse sampleDetailResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(salesOrderController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleOrderId = UUID.randomUUID();

        sampleDetailResponse = SalesOrderDetailResponse.builder()
                .orderId(sampleOrderId)
                .orderCode("SO-20261031-0001")
                .customerId(UUID.randomUUID())
                .customerName("Khách Hàng Mẫu")
                .customerPhone("0912345678")
                .salesStaffName("Nhân Viên Bán Hàng")
                .orderType("RETAIL")
                .orderChannel("IN_STORE")
                .orderStatus("COMPLETED")
                .paymentStatus("PAID")
                .orderDate(OffsetDateTime.now())
                .subtotal(new BigDecimal("500000"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("500000"))
                .deliveryAddress("Tại cửa hàng")
                .items(List.of())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/sales/orders -> Tạo đơn hàng thành công trả về 201 và MSG58")
    void createOrder_Success() throws Exception {
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
                .fulfillmentMethod(FulfillmentMethod.IN_STORE_PICKUP)
                .paymentMethod(PaymentMethod.CASH)
                .collectedAmount(new BigDecimal("500000"))
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .variantId(UUID.randomUUID())
                                .quantity(BigDecimal.ONE)
                                .build()
                ))
                .build();

        when(salesOrderService.createSalesOrder(any(CreateSalesOrderRequest.class))).thenReturn(sampleDetailResponse);

        mockMvc.perform(post("/api/v1/sales/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Sales order created successfully."))
                .andExpect(jsonPath("$.data.orderCode").value("SO-20261031-0001"))
                .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/v1/sales/orders -> Lấy danh sách đơn hàng thành công trả về 200")
    void listOrders_Success() throws Exception {
        SalesOrderSummaryResponse summary = SalesOrderSummaryResponse.builder()
                .orderId(sampleOrderId)
                .orderCode("SO-20261031-0001")
                .customerName("Khách Hàng Mẫu")
                .orderStatus("COMPLETED")
                .paymentStatus("PAID")
                .totalAmount(new BigDecimal("500000"))
                .build();

        when(salesOrderService.getSalesOrders(any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/sales/orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("keyword", "SO-20261031"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].orderCode").value("SO-20261031-0001"));
    }

    @Test
    @DisplayName("GET /api/v1/sales/orders/{orderId} -> Lấy chi tiết đơn hàng trả về 200")
    void getOrderDetail_Success() throws Exception {
        when(salesOrderService.getSalesOrderDetail(sampleOrderId)).thenReturn(sampleDetailResponse);

        mockMvc.perform(get("/api/v1/sales/orders/{orderId}", sampleOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(sampleOrderId.toString()));
    }

    @Test
    @DisplayName("PATCH /api/v1/sales/orders/{orderId}/pre-dispatch -> Chỉnh sửa đơn trả về 200")
    void updatePreDispatch_Success() throws Exception {
        UpdatePreDispatchOrderRequest updateReq = UpdatePreDispatchOrderRequest.builder()
                .deliveryAddress("Địa chỉ mới")
                .note("Giao sớm trước 5h")
                .build();

        when(salesOrderService.updatePreDispatch(eq(sampleOrderId), any(UpdatePreDispatchOrderRequest.class)))
                .thenReturn(sampleDetailResponse);

        mockMvc.perform(patch("/api/v1/sales/orders/{orderId}/pre-dispatch", sampleOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order updated successfully."));
    }

    @Test
    @DisplayName("POST /api/v1/sales/orders/{orderId}/cancel -> Hủy đơn trả về 200 và MSG64")
    void cancelOrder_Success() throws Exception {
        CancelOrderRequest cancelReq = CancelOrderRequest.builder()
                .cancelReason("Khách yêu cầu hủy đơn")
                .build();

        doNothing().when(salesOrderService).cancelOrder(eq(sampleOrderId), any(CancelOrderRequest.class));

        mockMvc.perform(post("/api/v1/sales/orders/{orderId}/cancel", sampleOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order cancellation approved and inventory released."));
    }

    @Test
    @DisplayName("POST /api/v1/sales/orders/{orderId}/cancel không có lý do -> Báo lỗi 400 Validation")
    void cancelOrder_MissingReason_Returns400() throws Exception {
        CancelOrderRequest invalidReq = CancelOrderRequest.builder()
                .cancelReason("")
                .build();

        mockMvc.perform(post("/api/v1/sales/orders/{orderId}/cancel", sampleOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());
    }
}
