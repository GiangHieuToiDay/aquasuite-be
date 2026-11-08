package com.aquasuite.ims.module.purchasing.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Snapshot công khai của một đơn mua hàng phục vụ nghiệp vụ nhập kho (UC-32).
 *
 * <p>Thuộc public contract của module Purchasing. Module Inventory dùng view này để kiểm tra
 * điều kiện nhận (trạng thái PO), xác định kho nhận và duyệt từng dòng hàng còn phải nhận.</p>
 *
 * @param purchaseOrderId khóa đơn mua hàng
 * @param poCode          mã đơn mua hàng hiển thị
 * @param status          trạng thái PO dưới dạng chuỗi (CONFIRMED, PARTIALLY_RECEIVED, ...)
 * @param receivable      true nếu PO đang ở trạng thái cho phép nhập kho (CONFIRMED hoặc PARTIALLY_RECEIVED)
 * @param warehouseCode   mã kho nhận hàng theo PO
 * @param supplierId      nhà cung cấp của đơn
 * @param items           danh sách dòng hàng kèm số còn phải nhận
 */
public record ReceivablePurchaseOrderView(
        UUID purchaseOrderId,
        String poCode,
        String status,
        boolean receivable,
        String warehouseCode,
        UUID supplierId,
        List<ReceivableItemView> items
) {}
