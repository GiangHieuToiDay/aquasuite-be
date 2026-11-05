package com.aquasuite.ims.module.sales.internal.enums;

/**
 * Phân loại loại đơn hàng trong hệ thống.
 */
public enum OrderType {
    /**
     * Đơn bán lẻ tại Showroom / POS / Cửa hàng / Online (giao 1 lần, thanh toán ngay/COD).
     */
    RETAIL,

    /**
     * Đơn công trình / dự án (có báo giá, chia nhiều đợt giao hàng, công nợ, lắp đặt kỹ thuật).
     */
    PROJECT
}
