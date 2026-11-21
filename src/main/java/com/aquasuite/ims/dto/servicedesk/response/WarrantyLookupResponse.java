package com.aquasuite.ims.dto.servicedesk.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

/** Customer-safe display projection. It does not expose another customer's identifiers or records. */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WarrantyLookupResponse(UUID warrantyRecordId, String productName, String variantName, String sku,
                                     String serialNumber, String orderCode, String warrantyTerms,
                                     LocalDate startDate, LocalDate endDate, long remainingDays,
                                     String status, boolean claimAllowed) {
}
