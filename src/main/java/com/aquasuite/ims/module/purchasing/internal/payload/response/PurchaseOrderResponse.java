package com.aquasuite.ims.module.purchasing.internal.payload.response;

import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponse {
    private UUID purchaseOrderId;
    private String poCode;
    private UUID supplierId;
    private String supplierName;
    private String supplierCode;
    private String supplierPhone;
    private UUID warehouseId;
    private String warehouseCode;
    private PurchaseOrderStatus status;
    private LocalDate orderDate;
    private LocalDate expectedDate;
    private BigDecimal totalAmount;
    private String note;
    private UUID createdBy;
    private String createdByName;
    private UUID confirmedBy;
    private String confirmedByName;
    private OffsetDateTime confirmedAt;
    private UUID cancelledBy;
    private String cancelledByName;
    private OffsetDateTime cancelledAt;
    private String cancelReason;
    private UUID closedBy;
    private String closedByName;
    private OffsetDateTime closedAt;
    private String closeReason;
    private Integer version;
    private String deliveryPlanType;
    private BigDecimal paidAmount;
    private Boolean isShortClosed;
    private UUID reopenedBy;
    private String reopenedByName;
    private OffsetDateTime reopenedAt;
    private String reopenReason;
    private Integer totalStages;
    private Integer settledStages;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<PurchaseOrderItemResponse> items;
    private List<PurchaseOrderReceiptItemResponse> receipts;
    private List<PurchaseOrderStageResponse> stages;
}