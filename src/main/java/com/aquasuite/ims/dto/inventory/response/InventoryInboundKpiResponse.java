package com.aquasuite.ims.dto.inventory.response;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Số liệu 4 thẻ KPI tổng hợp nhanh ở đầu trang Quản lý Tồn kho (tính trên toàn kho chính).
 */
@Builder
public record InventoryInboundKpiResponse(
        long totalSkus,
        long needOrderCount,
        long incomingSkuCount,
        BigDecimal totalIncomingQty,
        long openPoCount,
        long overduePoCount,
        long reservedSkuCount,
        BigDecimal totalReservedQty
) {}
