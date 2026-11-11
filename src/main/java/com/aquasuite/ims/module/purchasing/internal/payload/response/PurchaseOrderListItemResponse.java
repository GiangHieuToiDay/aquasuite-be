package com.aquasuite.ims.module.purchasing.internal.payload.response;

import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderListItemResponse {
    private UUID purchaseOrderId;
    private String poCode;
    private UUID supplierId;
    private String supplierName;
    private String supplierCode;
    private String warehouseCode;
    private PurchaseOrderStatus status;
    private LocalDate orderDate;
    private LocalDate expectedDate;
    private BigDecimal totalAmount;
    private Integer totalItems;
    private String createdByName;
    private OffsetDateTime createdAt;
}