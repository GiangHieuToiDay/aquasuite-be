package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.payload.request.CancelOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SalesOrderFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdatePreDispatchOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SalesOrderService {

    /**
     * Tạo mới đơn bán hàng lẻ trực tiếp tại quầy POS (UC-21).
     */
    SalesOrderDetailResponse createSalesOrder(CreateSalesOrderRequest request);

    /**
     * Lấy danh sách đơn bán hàng có lọc và phân trang.
     */
    Page<SalesOrderSummaryResponse> getSalesOrders(SalesOrderFilterRequest filter, Pageable pageable);

    /**
     * Xem thông tin chi tiết một đơn bán hàng.
     */
    SalesOrderDetailResponse getSalesOrderDetail(UUID orderId);

    /**
     * Chỉnh sửa thông tin đơn hàng trước khi xuất kho / giao hàng (pre-dispatch).
     */
    SalesOrderDetailResponse updatePreDispatch(UUID orderId, UpdatePreDispatchOrderRequest request);

    /**
     * Hủy đơn hàng và giải phóng tồn kho đã đặt trước (release reserved stock).
     */
    void cancelOrder(UUID orderId, CancelOrderRequest request);
}
