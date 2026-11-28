package com.aquasuite.ims.dto.sales.response;

import com.aquasuite.ims.enums.sales.OrderModificationRequestStatus;
import com.aquasuite.ims.enums.sales.OrderModificationRequestType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerOrderModificationRequestResponse(
        UUID id,
        UUID orderId,
        OrderModificationRequestType requestType,
        OrderModificationRequestStatus requestStatus,
        String customerReason,
        OffsetDateTime createdAt) {}
