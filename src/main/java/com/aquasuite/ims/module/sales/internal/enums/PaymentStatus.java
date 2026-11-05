package com.aquasuite.ims.module.sales.internal.enums;

/**
 * Trạng thái thanh toán của đơn hàng.
 */
public enum PaymentStatus {
    /** Chưa thanh toán */
    UNPAID,

    /** Đã thanh toán một phần */
    PARTIALLY_PAID,

    /** Đã thanh toán toàn bộ */
    PAID
}
