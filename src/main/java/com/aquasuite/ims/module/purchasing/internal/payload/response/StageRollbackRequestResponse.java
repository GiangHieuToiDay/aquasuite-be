package com.aquasuite.ims.module.purchasing.internal.payload.response;

import com.aquasuite.ims.module.purchasing.internal.enums.RollbackRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageRollbackRequestResponse {

    private UUID rollbackId;
    private UUID stageId;
    private UUID purchaseOrderId;
    private UUID requestedBy;
    private String requestedByName;
    private OffsetDateTime requestedAt;
    private String requestReason;
    private RollbackRequestStatus status;
    private UUID reviewedBy;
    private String reviewedByName;
    private OffsetDateTime reviewedAt;
    private String reviewNote;
    private OffsetDateTime createdAt;
}

