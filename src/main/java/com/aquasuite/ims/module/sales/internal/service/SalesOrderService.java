package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SalesOrderFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service nội bộ cho chức năng Tạo và Quản lý Đơn hàng Bán hàng tại quầy (UC-21).
 */
public interface SalesOrderService {

    /**
     * Tạo mới đơn hàng bán lẻ trực tiếp tại quầy POS.
     */
    SalesOrderDetailResponse createSalesOrder(CreateSalesOrderRequest request);

    /**
     * Lấy danh sách đơn hàng có phân trang, tìm kiếm và bộ lọc.
     */
    Page<SalesOrderSummaryResponse> getSalesOrders(SalesOrderFilterRequest filter, Pageable pageable);

    /**
     * Xem thông tin chi tiết đơn hàng.
     */
    SalesOrderDetailResponse getSalesOrderDetail(UUID orderId);
}
