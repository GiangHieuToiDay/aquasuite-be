package com.aquasuite.ims.module.sales.internal.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderMilestoneResponse {

    private UUID milestoneId;
    private String stageName;
    private BigDecimal percent;
    private BigDecimal amount;
    private Integer sortOrder;
}

