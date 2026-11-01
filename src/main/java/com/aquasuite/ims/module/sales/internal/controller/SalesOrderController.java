package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.sales.internal.payload.request.CancelOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SalesOrderFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdatePreDispatchOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderSummaryResponse;
import com.aquasuite.ims.module.sales.internal.service.SalesOrderService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller Quản lý Đơn hàng Bán lẻ (Sales Orders Management - UC-21).
 * Quyền yêu cầu: SALES_STAFF hoặc SHOP_OWNER
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sales/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES_STAFF', 'SHOP_OWNER')")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    /**
     * Tạo mới đơn bán lẻ trực tiếp tại quầy showroom (POS).
     * Thông báo thành công: MSG58 - "Sales order created successfully."
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> createOrder(
            @Valid @RequestBody CreateSalesOrderRequest request
    ) {
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Sales order created successfully."));
    }

    /**
     * Lấy danh sách đơn hàng có phân trang, tìm kiếm và bộ lọc đa tiêu chí.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<SalesOrderSummaryResponse>>> listOrders(
            SalesOrderFilterRequest filter,
            @PageableDefault(sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SalesOrderSummaryResponse> page = salesOrderService.getSalesOrders(filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    /**
     * Xem thông tin chi tiết đơn hàng (dòng sản phẩm, thông tin khách hàng, trạng thái thanh toán).
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> getOrderDetail(
            @PathVariable UUID orderId
    ) {
        SalesOrderDetailResponse detail = salesOrderService.getSalesOrderDetail(orderId);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    /**
     * Chỉnh sửa thông tin đơn hàng trước khi xuất kho / giao hàng (pre-dispatch).
     */
    @PatchMapping("/{orderId}/pre-dispatch")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> updatePreDispatch(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdatePreDispatchOrderRequest request
    ) {
        SalesOrderDetailResponse updated = salesOrderService.updatePreDispatch(orderId, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Order updated successfully."));
    }

    /**
     * Hủy đơn hàng và tự động giải phóng tồn kho đã đặt trước (release reserved stock).
     * Thông báo thành công: MSG64 - "Order cancellation approved and inventory released."
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        salesOrderService.cancelOrder(orderId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Order cancellation approved and inventory released."));
    }
}
