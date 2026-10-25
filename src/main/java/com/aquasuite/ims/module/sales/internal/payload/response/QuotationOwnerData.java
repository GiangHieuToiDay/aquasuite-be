package com.aquasuite.ims.module.sales.internal.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuotationOwnerData {

    private BigDecimal grossProfit;
    private BigDecimal marginPercent;
    private BigDecimal marginPercentAtApproval;
    private String approvalNote;

    @Builder.Default
    private List<QuotationItemCostResponse> itemCosts = new ArrayList<>();
}
