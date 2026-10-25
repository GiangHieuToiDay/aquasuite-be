package com.aquasuite.ims.module.sales.internal.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuotationSummaryResponse {

    private BigDecimal listPriceTotal;
    private BigDecimal totalDiscount;
    private BigDecimal netAmount;
    private BigDecimal vatRate;
    private BigDecimal vatAmount;
    private BigDecimal shippingFee;
    private BigDecimal grandTotal;
}
