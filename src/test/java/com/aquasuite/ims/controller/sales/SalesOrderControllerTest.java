package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.enums.sales.FulfillmentMethod;
import com.aquasuite.ims.enums.sales.PaymentMethod;
import com.aquasuite.ims.exception.sales.SalesErrorCode;
import com.aquasuite.ims.dto.sales.request.ConfirmOnlineOrderRequest;
import com.aquasuite.ims.dto.sales.request.CreateOrderItemRequest;
import com.aquasuite.ims.dto.sales.request.CreateSalesOrderRequest;
import com.aquasuite.ims.dto.sales.request.RejectOnlineOrderRequest;
import com.aquasuite.ims.dto.sales.response.SalesOrderDetailResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderSummaryResponse;
import com.aquasuite.ims.service.sales.SalesOrderService;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                                .orderCode("SO-20261103-0001")
                                .customerId(UUID.randomUUID())
                                .customerName("Khách Hàng Mẫu")
                                .customerPhone("0912345678")
                                .salesStaffName("Nhân Viên Bán Hàng")
                                .orderType("RETAIL")
                                .orderChannel("COUNTER")
                                .orderStatus("PROCESSING")
                                .paymentStatus("UNPAID")
                                .orderDate(OffsetDateTime.now())
                                .subtotal(new BigDecimal("5000000"))
                                .discountAmount(BigDecimal.ZERO)
                                .totalAmount(new BigDecimal("5000000"))
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
                                .items(List.of(
                                                CreateOrderItemRequest.builder()
                                                                .variantId(UUID.randomUUID())
                                                                .quantity(BigDecimal.ONE)
                                                                .build()))
                                .build();

                when(salesOrderService.createSalesOrder(any(CreateSalesOrderRequest.class)))
                                .thenReturn(sampleDetailResponse);

                mockMvc.perform(post("/api/v1/sales/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.message").value("Sales order created successfully."))
                                .andExpect(jsonPath("$.data.orderCode").value("SO-20261103-0001"))
                                .andExpect(jsonPath("$.data.orderStatus").value("PROCESSING"))
                                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"));
        }

        @Test
        @DisplayName("GET /api/v1/sales/orders -> Lấy danh sách đơn hàng thành công trả về 200")
        void listOrders_Success() throws Exception {
                SalesOrderSummaryResponse summary = SalesOrderSummaryResponse.builder()
                                .orderId(sampleOrderId)
                                .orderCode("SO-20261103-0001")
                                .customerName("Khách Hàng Mẫu")
                                .orderStatus("PROCESSING")
                                .paymentStatus("UNPAID")
                                .totalAmount(new BigDecimal("5000000"))
                                .build();

                when(salesOrderService.getSalesOrders(any(), any()))
                                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 10), 1));

                mockMvc.perform(get("/api/v1/sales/orders")
                                .param("page", "0")
                                .param("size", "10")
                                .param("keyword", "SO-20261103"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.content[0].orderCode").value("SO-20261103-0001"));
        }

        @Test
        @DisplayName("GET /api/v1/sales/orders/{orderId} -> Lấy chi tiết đơn hàng trả về 200")
        void getOrderDetail_Success() throws Exception {
                when(salesOrderService.getSalesOrderDetail(sampleOrderId)).thenReturn(sampleDetailResponse);

                mockMvc.perform(get("/api/v1/sales/orders/{orderId}", sampleOrderId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.orderId").value(sampleOrderId.toString()))
                                .andExpect(jsonPath("$.data.orderCode").value("SO-20261103-0001"));
        }

        @Test
        @DisplayName("GET /api/v1/sales/orders/{orderId} không tồn tại -> Trả về 404 Not Found")
        void getOrderDetail_NotFound_Returns404() throws Exception {
                UUID unknownId = UUID.randomUUID();
                when(salesOrderService.getSalesOrderDetail(unknownId))
                                .thenThrow(new AppException(SalesErrorCode.ORDER_NOT_FOUND));

                mockMvc.perform(get("/api/v1/sales/orders/{orderId}", unknownId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.success").value(false))
                                .andExpect(jsonPath("$.code").value("SALES_003"));
        }

        @Test
        @DisplayName("PATCH /api/v1/sales/orders/{orderId}/confirm -> Xác nhận đơn hàng Online trả về 200")
        void confirmOnlineOrder_Success() throws Exception {
                ConfirmOnlineOrderRequest request = ConfirmOnlineOrderRequest.builder()
                                .deliveryAddress("123 Lê Lợi, Q.1")
                                .deliverySchedule("Giao sáng mai")
                                .note("Đã gọi xác nhận")
                                .build();

                when(salesOrderService.confirmOnlineOrder(eq(sampleOrderId), any(ConfirmOnlineOrderRequest.class)))
                                .thenReturn(sampleDetailResponse);

                mockMvc.perform(patch("/api/v1/sales/orders/{orderId}/confirm", sampleOrderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.orderStatus").value("PROCESSING"));
        }

        @Test
        @DisplayName("PATCH /api/v1/sales/orders/{orderId}/reject -> Từ chối/Hủy đơn hàng Online trả về 200")
        void rejectOnlineOrder_Success() throws Exception {
                RejectOnlineOrderRequest request = RejectOnlineOrderRequest.builder()
                                .reason("Khách yêu cầu hủy đơn")
                                .build();

                SalesOrderDetailResponse cancelledDetail = SalesOrderDetailResponse.builder()
                                .orderId(sampleOrderId)
                                .orderCode("SO-20261103-0001")
                                .orderStatus("CANCELLED")
                                .paymentStatus("UNPAID")
                                .build();

                when(salesOrderService.rejectOnlineOrder(eq(sampleOrderId), any(RejectOnlineOrderRequest.class)))
                                .thenReturn(cancelledDetail);

                mockMvc.perform(patch("/api/v1/sales/orders/{orderId}/reject", sampleOrderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"));
        }

        @Test
        @DisplayName("POST /api/v1/sales/orders/{orderId}/payos/sync -> Đồng bộ PayOS đơn hàng Online trả về 200")
        void syncOnlineOrderPayosPayment_Success() throws Exception {
                when(salesOrderService.syncOnlineOrderPayosPayment(sampleOrderId))
                                .thenReturn(sampleDetailResponse);

                mockMvc.perform(post("/api/v1/sales/orders/{orderId}/payos/sync", sampleOrderId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.orderId").value(sampleOrderId.toString()));
        }

        @Test
        @DisplayName("GET /api/v1/sales/orders/me và /me/{orderId} -> Trả về đơn hàng của khách hàng 200")
        void getMyCustomerOrdersAndDetail_Success() throws Exception {
                when(salesOrderService.getMyCustomerOrders()).thenReturn(List.of());
                when(salesOrderService.getMyCustomerOrderDetail(sampleOrderId)).thenReturn(sampleDetailResponse);

                mockMvc.perform(get("/api/v1/sales/orders/me"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true));

                mockMvc.perform(get("/api/v1/sales/orders/me/{orderId}", sampleOrderId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.orderId").value(sampleOrderId.toString()));
        }
}
