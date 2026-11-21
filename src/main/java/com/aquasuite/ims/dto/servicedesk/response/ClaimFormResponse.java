package com.aquasuite.ims.dto.servicedesk.response;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dữ liệu điền sẵn và thông tin điều kiện cho form yêu cầu bảo hành trực tuyến (UC-47).
 */
@Builder
public record ClaimFormResponse(
        UUID warrantyRecordId,
        String warrantyCode,
        String productName,
        String variantName,
        String sku,
        String serialNumber,
        LocalDate startDate,
        LocalDate endDate,
        boolean expired,
        boolean canSubmit,
        String blockedReason,
        String openTicketCode,
        String contactName,
        String contactPhone,
        String contactEmail,
        String suggestedSiteAddress,
        List<String> customerAddresses
) {
}

