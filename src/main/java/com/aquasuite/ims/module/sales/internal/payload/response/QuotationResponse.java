package com.aquasuite.ims.module.sales.internal.payload.response;

import com.aquasuite.ims.module.sales.internal.enums.QuotationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuotationResponse {

    // Identity
    private UUID quotationId;
    private String quotationCode;
    private Integer revisionNo;
    private UUID parentQuotationId;
    private QuotationStatus status;

    // Dates
    private LocalDate quotationDate;
    private LocalDate validUntil;

    // Customer & Project
    private UUID customerId;
    private String customerName;
    private String projectName;
    private String deliveryAddress;
    private String note;

    // Line items
    @Builder.Default
    private List<QuotationItemResponse> items = new ArrayList<>();

    // Financial summary
    private QuotationSummaryResponse summary;

    // Approval info
    private QuotationApprovalInfo approval;

    // Milestones
    private OffsetDateTime sentAt;
    private String pdfUrl;
    private OffsetDateTime acceptedAt;
    private String signedFileUrl;
    private OffsetDateTime lostAt;
    private String lostReason;

    // Audit
    private UUID createdBy;

    // Confidential owner data (null and excluded for staff)
    private QuotationOwnerData ownerData;
}
