package com.aquasuite.ims.module.servicedesk.internal.payload.res;

import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyDisplayStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record WarrantyRecordDetailResponse(
        UUID warrantyRecordId,
        String warrantyCode,
        WarrantyStatus status,
        WarrantyDisplayStatus displayStatus,
        Boolean canRequestSupport,
        String openTicketCode,
        LocalDate startDate,
        LocalDate endDate,
        Integer warrantyMonths,
        long remainingDays,
        String voidReason,
        OffsetDateTime voidedAt,
        UUID voidedBy,
        UUID createdBy,
        OffsetDateTime createdAt,

        UUID variantId,
        String sku,
        String productName,
        String variantName,
        Boolean serialTracking,
        String serialNumber,

        UUID warrantyPolicyId,
        String policyCode,
        String policyName,
        String warrantyTerms,

        UUID customerId,
        String customerCode,
        String customerName,
        String phoneNumber,
        String customerEmail,
        String installationAddress,

        OrderInvoiceInfo orderInvoice,
        DeliveryNoteInfo deliveryNote,
        List<RepairHistoryItem> repairHistory
) {
    @Builder
    public record OrderInvoiceInfo(
            UUID orderId,
            UUID orderItemId,
            String orderCode,
            String orderStatus,
            String paymentStatus,
            OffsetDateTime orderDate,
            String deliveryAddress,
            UUID invoiceId,
            String invoiceCode,
            String invoiceType,
            String invoiceStatus,
            OffsetDateTime issueDate,
            BigDecimal invoiceTotalAmount
    ) {
    }

    @Builder
    public record DeliveryNoteInfo(
            UUID deliveryId,
            String deliveryCode,
            String status,
            OffsetDateTime deliveredAt,
            String note
    ) {
    }

    @Builder
    public record RepairHistoryItem(
            UUID technicalJobId,
            TechnicalJobType jobType,
            TechnicalJobStatus status,
            OffsetDateTime scheduledAt,
            String issueDescription,
            String result,
            UUID technicianId,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt
    ) {
    }
}

