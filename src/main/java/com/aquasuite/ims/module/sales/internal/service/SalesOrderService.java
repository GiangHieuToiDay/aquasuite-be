package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.payload.request.ConvertQuotationToOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SalesOrderFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service nội bộ cho chức năng Tạo và Quản lý Đơn hàng Bán hàng tại quầy (UC-21)
 * và Chuyển đổi Báo giá thành Đơn hàng công trình (UC-28 / UC-073).
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

    /**
     * Chuyển đổi báo giá dự án (Quotation) đã chốt/duyệt thành đơn hàng công trình (Project Sales Order) - UC-28 / UC-073.
     */
    SalesOrderDetailResponse convertQuotationToOrder(UUID quotationId, ConvertQuotationToOrderRequest request);

    default SalesOrderDetailResponse convertQuotationToOrder(UUID quotationId) {
        return convertQuotationToOrder(quotationId, null);
    }
}
