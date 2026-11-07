package com.aquasuite.ims.module.purchasing.internal.payload.request;

import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderFilterRequest {

    private String search;
    private UUID supplierId;
    private PurchaseOrderStatus status;
    private LocalDate fromDate;
    private LocalDate toDate;
    @Builder.Default
    private Integer page = 0;
    @Builder.Default
    private Integer size = 10;
    @Builder.Default
    private String sortBy = "createdAt";
    @Builder.Default
    private String sortDirection = "DESC";
}