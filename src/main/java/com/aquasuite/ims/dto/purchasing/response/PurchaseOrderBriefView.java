package com.aquasuite.ims.dto.purchasing.response;

import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Thông tin tóm tắt đơn mua hàng dùng để làm giàu dữ liệu thẻ kho và phiếu nhập kho.
 */
@Builder
public record PurchaseOrderBriefView(
        UUID purchaseOrderId,
        String poCode,
        String status,
        String statusLabel,
        UUID supplierId,
        String supplierCode,
        String supplierName,
        LocalDate orderDate,
        LocalDate expectedDate
) {}
