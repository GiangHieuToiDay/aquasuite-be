package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.request.*;
import com.aquasuite.ims.dto.sales.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service nội bộ cho chức năng Tạo và Quản lý Đơn hàng Bán hàng tại quầy (UC-21),
 * Chuyển đổi Báo giá thành Đơn hàng công trình (UC-28 / UC-073) và Yêu cầu Chỉnh sửa Đơn (UC-26) hợp nhất.
 */
public interface SalesOrderService {

    // ── Sales Order Operations ───────────────────────────────────────────────

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

    /**
     * Xác nhận đơn hàng trực tuyến (UC-22: PENDING_CONFIRMATION -> PROCESSING),
     * đặt giữ tồn kho và khởi tạo hóa đơn cửa hàng nháp (STORE - DRAFT).
     */
    SalesOrderDetailResponse confirmOnlineOrder(UUID orderId, ConfirmOnlineOrderRequest request);

    /**
     * Từ chối hoặc hủy đơn hàng trực tuyến khi đang chờ xác nhận (UC-22: PENDING_CONFIRMATION -> CANCELLED).
     */
    SalesOrderDetailResponse rejectOnlineOrder(UUID orderId, RejectOnlineOrderRequest request);

    /**
     * Đồng bộ trạng thái thanh toán PayOS của đơn hàng trực tuyến dành cho nhân viên (UC-22).
     */
    SalesOrderDetailResponse syncOnlineOrderPayosPayment(UUID orderId);

    Optional<SalesOrderPaymentInfoDto> getOrderPaymentInfo(UUID orderId);

    Optional<SalesOrderPaymentInfoDto> lockOrderPaymentInfo(UUID orderId);

    void updatePaymentStatus(UUID orderId, String paymentStatus);

    List<SalesOrderMilestoneDto> getOrderPaymentMilestones(UUID orderId);

    Optional<SalesOrderItemSummaryDto> getOrderItemSummary(UUID orderItemId);

    // ── Order Modification Request Operations (UC-26) ────────────────────────

    Page<OrderModificationRequestResponse> list(Pageable pageable, boolean accountantOnly);

    OrderModificationRequestResponse detail(UUID requestId, boolean accountantOnly);

    OrderModificationRequestResponse create(CreateOrderModificationRequest body, UUID actorId);

    OrderModificationRequestResponse reject(UUID requestId, ReviewOrderModificationRequest request, UUID actorId);

    OrderModificationRequestResponse approve(UUID requestId, ApproveOrderModificationRequest request, UUID actorId, boolean shopOwner);

    OrderModificationRequestResponse recordTaxResult(UUID requestId, RecordOrderModificationTaxResultRequest request, UUID actorId);

    void sendResultEmail(UUID requestId, UUID actorId);
}
