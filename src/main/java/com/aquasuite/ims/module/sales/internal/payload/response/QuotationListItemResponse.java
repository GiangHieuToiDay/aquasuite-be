package com.aquasuite.ims.module.sales.internal.payload.response;

import com.aquasuite.ims.module.sales.internal.enums.QuotationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuotationListItemResponse {

    private UUID quotationId;
    private String quotationCode;
    private Integer revisionNo;
    private String customerName;
    private String projectName;
    private QuotationStatus status;
    private LocalDate quotationDate;
    private LocalDate validUntil;
    private BigDecimal totalAmount;
    private String createdByName;
}
