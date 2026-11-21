package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.request.ConvertQuotationToOrderRequest;
import com.aquasuite.ims.dto.sales.request.CreateSalesOrderRequest;
import com.aquasuite.ims.dto.sales.request.SalesOrderFilterRequest;
import com.aquasuite.ims.dto.sales.request.UpdateOrderFulfillmentRequest;
import com.aquasuite.ims.dto.sales.response.SalesOrderDetailResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderSummaryResponse;
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

    /**
     * Cập nhật thông tin giao nhận và điều phối (địa chỉ, lịch giao, ghi chú)
     * khi đơn hàng đang ở trạng thái PROCESSING.
     */
    SalesOrderDetailResponse updateFulfillmentInfo(UUID orderId, UpdateOrderFulfillmentRequest request);

    /**
     * Xác nhận hoàn tất đơn hàng (chuyển PROCESSING -> COMPLETED)
     * khi đơn hàng đã thanh toán đủ (PAID).
     */
    SalesOrderDetailResponse completeSalesOrder(UUID orderId);

    java.util.Optional<com.aquasuite.ims.dto.sales.response.SalesOrderPaymentInfoDto> getOrderPaymentInfo(UUID orderId);

    java.util.Optional<com.aquasuite.ims.dto.sales.response.SalesOrderPaymentInfoDto> lockOrderPaymentInfo(UUID orderId);

    void updatePaymentStatus(UUID orderId, String paymentStatus);

    java.util.List<com.aquasuite.ims.dto.sales.response.SalesOrderMilestoneDto> getOrderPaymentMilestones(UUID orderId);

    java.util.Optional<com.aquasuite.ims.dto.sales.response.SalesOrderItemSummaryDto> getOrderItemSummary(UUID orderItemId);
}
