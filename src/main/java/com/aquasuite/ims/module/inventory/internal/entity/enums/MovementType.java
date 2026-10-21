package com.aquasuite.ims.module.inventory.internal.entity.enums;

public enum MovementType {
    INITIAL_IMPORT, // Khởi tạo tồn kho đầu kỳ
    GOODS_RECEIPT, // Nhập hàng từ nhà cung cấp
    STOCK_DISPATCH, // Xuất kho giao đơn hàng
    RETURN_IN, // Khách trả hàng nhập lại kho
    SUPPLIER_RETURN, // Trả hàng cho nhà cung cấp
    ADJUSTMENT_IN, // Kiểm kê tăng tồn kho
    ADJUSTMENT_OUT // Kiểm kê giảm tồn kho
}
