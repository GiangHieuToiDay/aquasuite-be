package com.aquasuite.ims.dto.inventory.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Thông tin tóm tắt lượng hàng đang chờ về từ PO theo từng SKU trên bảng Quản lý Tồn kho.
 *
 * @param restockActionHint NEED_ORDER | INCOMING_PARTIAL | INCOMING_COVERED | NONE
 */
@Builder
public record VariantInboundSummaryResponse(
        UUID variantId,
        BigDecimal incomingQty,
        int openPoCount,
        LocalDate nearestExpectedDate,
        boolean hasOverduePo,
        BigDecimal suggestedOrderQty,
        String restockActionHint
) {}
