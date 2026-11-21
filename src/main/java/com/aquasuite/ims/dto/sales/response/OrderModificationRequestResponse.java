package com.aquasuite.ims.dto.sales.response;

import com.aquasuite.ims.enums.sales.OrderModificationRequestStatus;
import com.aquasuite.ims.enums.sales.OrderModificationRequestType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderModificationRequestResponse {
    UUID id;
    UUID orderId;
    UUID customerId;
    OrderModificationRequestType requestType;
    OrderModificationRequestStatus requestStatus;
    String customerReason;
    String decisionNote;
    String requestedDeliveryAddress;
    UUID requestedBy;
    UUID reviewedBy;
    OffsetDateTime reviewedAt;
    UUID replacementOrderId;
    UUID originalInvoiceId;
    UUID resultingInvoiceId;
    String taxResultReference;
    BigDecimal priceDifference;
    UUID refundTransactionId;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
    List<OrderModificationRequestItemResponse> items;
}
