package com.aquasuite.ims.module.servicedesk.internal.payload.res;

import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyDisplayStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record WarrantyRecordListItemResponse(
        UUID warrantyRecordId,
        String warrantyCode,
        UUID variantId,
        String sku,
        String productName,
        String variantName,
        String serialNumber,
        UUID customerId,
        String customerName,
        String phoneNumber,
        UUID orderId,
        String orderCode,
        LocalDate startDate,
        LocalDate endDate,
        long remainingDays,
        Integer warrantyMonths,
        WarrantyStatus status,
        WarrantyDisplayStatus displayStatus,
        Boolean canRequestSupport,
        String openTicketCode,
        OffsetDateTime createdAt
) {
}

