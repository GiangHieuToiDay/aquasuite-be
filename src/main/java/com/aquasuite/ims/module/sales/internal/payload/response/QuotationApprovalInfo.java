package com.aquasuite.ims.module.sales.internal.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuotationApprovalInfo {

    private Boolean requiresApproval;
    private BigDecimal maxDiscountRate;
    private BigDecimal staffDiscountLimit;
    private UUID approvedBy;
    private OffsetDateTime approvedAt;
}
