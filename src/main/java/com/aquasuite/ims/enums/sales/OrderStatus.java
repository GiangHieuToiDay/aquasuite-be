package com.aquasuite.ims.enums.sales;

/**
 * Trạng thái của đơn hàng trong hệ sinh thái bán hàng AquaSuite.
 */
public enum OrderStatus {
    /** Đơn hàng nháp (chờ duyệt hạn mức công nợ) */
    DRAFT,

    /** Đơn hàng Online mới đặt, chờ nhân viên bán hàng xác nhận (UC-18 / UC-22) */
    PENDING_CONFIRMATION,

    /** Đơn hàng đang chờ xử lý hoặc chờ giao hàng */
    PROCESSING,

    /** Đơn hàng đã hoàn tất (đã thanh toán đủ và nhận hàng tại quầy hoặc đã giao thành công) */
    COMPLETED,

    /** Đơn hàng đã bị hủy */
    CANCELLED
}
