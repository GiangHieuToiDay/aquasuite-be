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
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuotationItemResponse {

    private UUID quotationItemId;
    private UUID variantId;
    private String sku;
    private String variantName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountRate;
    private BigDecimal discountAmount;
    private BigDecimal lineTotal;
    private BigDecimal backorderQty;
    private Boolean isBackorder;
    private Integer leadTimeDays;
    private Integer sortOrder;

    @Builder.Default
    private List<QuotationComponentResponse> components = new ArrayList<>();
}
