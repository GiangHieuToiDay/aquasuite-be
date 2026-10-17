package com.aquasuite.ims.module.catalog.internal.payload.excel;

public enum ImportMode {
    /** Bỏ qua nếu trùng SKU (Chỉ tạo mới) */
    SKIP_EXISTING,

    /** Cập nhật thông tin nếu đã tồn tại SKU (Ghi đè) */
    OVERWRITE_EXISTING
}
