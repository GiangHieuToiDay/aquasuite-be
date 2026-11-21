package com.aquasuite.ims.enums.sales;

/**
 * Hình thức thanh toán đơn hàng.
 */
public enum PaymentMethod {
    /** Tiền mặt */
    CASH,

    /** Chuyển khoản ngân hàng */
    BANK_TRANSFER,

    /** Thẻ thanh toán (POS Card) */
    CARD,

    /** Mua hàng nợ / công nợ (dành cho đối tác/thầu thợ) */
    CREDIT
}
