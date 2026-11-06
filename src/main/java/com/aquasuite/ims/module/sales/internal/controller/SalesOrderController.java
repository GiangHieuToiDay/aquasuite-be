package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.sales.internal.payload.request.ConvertQuotationToOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SalesOrderFilterRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller Tạo và Quản lý Đơn hàng Bán hàng tại quầy (Sales Orders Management - UC-21)
 * và Chuyển đổi Báo giá thành Đơn hàng (UC-28 / UC-073).
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
     * Tạo đơn hàng công trình từ báo giá dự án đã chấp thuận (UC-28 / UC-073).
     */
    @PostMapping("/from-quotation/{quotationId}")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> createOrderFromQuotation(
            @PathVariable UUID quotationId,
            @RequestBody(required = false) ConvertQuotationToOrderRequest request
    ) {
        log.info("API: Create sales order from quotation quotationId={}", quotationId);
        SalesOrderDetailResponse response = salesOrderService.convertQuotationToOrder(quotationId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Chuyển báo giá thành đơn hàng công trình thành công!"));
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
     * Xem thông tin chi tiết đơn hàng (dòng sản phẩm, thông tin khách hàng, tổng tiền, trạng thái).
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> getOrderDetail(
            @PathVariable UUID orderId
    ) {
        SalesOrderDetailResponse detail = salesOrderService.getSalesOrderDetail(orderId);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }
}
