package com.aquasuite.ims.module.servicedesk.internal.enums;

/**
 * Trạng thái lưu trữ chính thức của thẻ bảo hành trong cơ sở dữ liệu.
 * Các trạng thái suy diễn như {@link WarrantyDisplayStatus#EXPIRED} hoặc
 * {@link WarrantyDisplayStatus#CLAIMED} được tính động khi đọc dữ liệu.
 */
public enum WarrantyStatus {
    ACTIVE,
    VOIDED
}

