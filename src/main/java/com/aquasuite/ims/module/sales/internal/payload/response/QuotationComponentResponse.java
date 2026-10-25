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
public class QuotationComponentResponse {

    private String sku;
    private String variantName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice; // Giá tham khảo
    private BigDecimal backorderQty;
    private Integer leadTimeDays;
}
