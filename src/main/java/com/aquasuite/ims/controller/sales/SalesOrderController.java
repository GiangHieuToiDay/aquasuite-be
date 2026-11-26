package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.dto.sales.request.*;
import com.aquasuite.ims.dto.sales.response.OrderModificationRequestResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderDetailResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderPageResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderSummaryResponse;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.sales.SalesOrderService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller Quản lý Đơn hàng Bán hàng (Sales Orders) và Yêu cầu Chỉnh sửa / Đổi trả đơn.
 */
@Slf4j
@RestController
public class SalesOrderController {

    private final SalesOrderService salesOrderService;
    private final IamQueryService iamApi;

    @Autowired
    public SalesOrderController(
            SalesOrderService salesOrderService,
            @Autowired(required = false) IamQueryService iamApi) {
        this.salesOrderService = salesOrderService;
        this.iamApi = iamApi;
    }

    public SalesOrderController(SalesOrderService salesOrderService) {
        this(salesOrderService, null);
    }

    // ── Sales Orders (/api/v1/sales/orders) ──────────────────────────────────

    @PostMapping("/api/v1/sales/orders")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SHOP_OWNER')")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> createOrder(
            @Valid @RequestBody CreateSalesOrderRequest request) {
        SalesOrderDetailResponse response = salesOrderService.createSalesOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Sales order created successfully."));
    }

    @PostMapping("/api/v1/sales/orders/from-quotation/{quotationId}")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SHOP_OWNER')")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> createOrderFromQuotation(
            @PathVariable UUID quotationId,
            @Valid @RequestBody(required = false) ConvertQuotationToOrderRequest request) {
        log.info("API: Create sales order from quotation quotationId={}", quotationId);
        SalesOrderDetailResponse response = salesOrderService.convertQuotationToOrder(quotationId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Chuyển báo giá thành đơn hàng công trình thành công!"));
    }

    @GetMapping("/api/v1/sales/orders")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SHOP_OWNER', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<SalesOrderPageResponse>> listOrders(
            @Valid SalesOrderFilterRequest filter,
            @PageableDefault(sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SalesOrderSummaryResponse> page = salesOrderService.getSalesOrders(filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok(SalesOrderPageResponse.from(page)));
    }

    @GetMapping("/api/v1/sales/orders/{orderId}")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SHOP_OWNER', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> getOrderDetail(@PathVariable UUID orderId) {
        SalesOrderDetailResponse detail = salesOrderService.getSalesOrderDetail(orderId);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @PatchMapping("/api/v1/sales/orders/{orderId}/fulfillment")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> updateFulfillment(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderFulfillmentRequest request) {
        log.info("API: Update fulfillment for orderId={}", orderId);
        SalesOrderDetailResponse response = salesOrderService.updateFulfillmentInfo(orderId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Cập nhật thông tin giao nhận thành công!"));
    }

    @PatchMapping("/api/v1/sales/orders/{orderId}/complete")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> completeOrder(@PathVariable UUID orderId) {
        log.info("API: Complete sales order orderId={}", orderId);
        SalesOrderDetailResponse response = salesOrderService.completeSalesOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Hoàn tất đơn hàng thành công!"));
    }

    // ── Order Modification Requests (/api/v1/sales/order-modification-requests)

    @GetMapping("/api/v1/sales/order-modification-requests")
    @PreAuthorize("hasAnyRole('SALES_STAFF','ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<Page<OrderModificationRequestResponse>> listModificationRequests(Pageable pageable) {
        return ApiResponse.ok(getModService().list(pageable, hasRole("ACCOUNTANT") && !hasRole("SHOP_OWNER")));
    }

    @GetMapping("/api/v1/sales/order-modification-requests/{requestId}")
    @PreAuthorize("hasAnyRole('SALES_STAFF','ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<OrderModificationRequestResponse> getModificationDetail(@PathVariable UUID requestId) {
        boolean accountantOnly = hasRole("ACCOUNTANT") && !hasRole("SHOP_OWNER") && !hasRole("SALES_STAFF");
        return ApiResponse.ok(getModService().detail(requestId, accountantOnly));
    }

    @PostMapping("/api/v1/sales/order-modification-requests")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ResponseEntity<ApiResponse<OrderModificationRequestResponse>> createModificationRequest(
            @Valid @RequestBody CreateOrderModificationRequest request) {
        OrderModificationRequestResponse response = getModService().create(request, iamApi.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Order modification request created successfully."));
    }

    @PostMapping("/api/v1/sales/order-modification-requests/{requestId}/reject")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<OrderModificationRequestResponse> rejectModificationRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody ReviewOrderModificationRequest request) {
        return ApiResponse.ok(getModService().reject(requestId, request, iamApi.getCurrentUserId()),
                "Order modification request rejected successfully.");
    }

    @PostMapping("/api/v1/sales/order-modification-requests/{requestId}/approve")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<OrderModificationRequestResponse> approveModificationRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody ApproveOrderModificationRequest request) {
        return ApiResponse.ok(getModService().approve(requestId, request, iamApi.getCurrentUserId(), hasRole("SHOP_OWNER")),
                "Order modification request reviewed successfully.");
    }

    @PostMapping("/api/v1/sales/order-modification-requests/{requestId}/tax-result")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<OrderModificationRequestResponse> recordTaxResult(
            @PathVariable UUID requestId,
            @Valid @RequestBody RecordOrderModificationTaxResultRequest request) {
        return ApiResponse.ok(getModService().recordTaxResult(requestId, request, iamApi.getCurrentUserId()),
                "Tax adjustment or replacement result recorded successfully.");
    }

    @PostMapping("/api/v1/sales/order-modification-requests/{requestId}/send-result-email")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<Void> sendModificationResultEmail(@PathVariable UUID requestId) {
        getModService().sendResultEmail(requestId, iamApi.getCurrentUserId());
        return ApiResponse.ok(null, "Order modification result email accepted for delivery.");
    }

    private SalesOrderService getModService() {
        return salesOrderService;
    }

    private boolean hasRole(String role) {
        if (iamApi == null) return false;
        var current = iamApi.getCurrentUser();
        return current != null && current.roles() != null && current.roles().contains(role);
    }
}
