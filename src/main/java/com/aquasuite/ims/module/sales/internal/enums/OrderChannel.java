package com.aquasuite.ims.module.sales.internal.enums;

/**
 * Kênh bán hàng phát sinh đơn.
 */
public enum OrderChannel {
    /** Kênh bán tại quầy theo contract mới. */
    COUNTER,
    /** Alias cũ chỉ kênh bán hàng tại quầy, không phải phương thức trả bằng thẻ. */
    POS,

    /** Bán hàng trực tuyến / Web Storefront */
    ONLINE,

    /** Kênh bán hàng dự án / Direct Sales */
    DIRECT_SALES
}
