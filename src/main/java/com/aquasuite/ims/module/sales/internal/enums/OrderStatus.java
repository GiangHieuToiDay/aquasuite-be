package com.aquasuite.ims.module.sales.internal.enums;

/**
 * Trạng thái của đơn hàng trong hệ sinh thái bán hàng AquaSuite.
 */
public enum OrderStatus {
    /** Đơn hàng đang chờ xử lý hoặc chờ giao hàng */
    PROCESSING,

    /** Đơn hàng đã hoàn tất (đã thanh toán đủ và nhận hàng tại quầy hoặc đã giao thành công) */
    COMPLETED,

    /** Đơn hàng đã bị hủy */
    CANCELLED
}
