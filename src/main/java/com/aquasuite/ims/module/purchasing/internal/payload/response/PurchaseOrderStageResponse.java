package com.aquasuite.ims.module.purchasing.internal.payload.response;

import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStageStatus;
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
public class PurchaseOrderStageResponse {

    private UUID stageId;
    private UUID purchaseOrderId;
    private String stageName;
    private String description;
    private LocalDate expectedDeliveryDate;
    private BigDecimal percent;
    private BigDecimal amount;
    private PurchaseOrderStageStatus status;
    private BigDecimal actualAmount;
    private OffsetDateTime paidAt;
    private UUID paidBy;
    private String paidByName;
    private String paymentMethod;
    private String paymentReference;
    private String settlementNote;
    private Integer sortOrder;
    private Integer version;
    private Boolean hasPendingRollback;
    private StageRollbackRequestResponse latestRollbackRequest;
}

