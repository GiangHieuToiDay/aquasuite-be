package com.aquasuite.ims.module.sales.internal.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ApproveOrderModificationRequest {
    @NotBlank @Size(max = 2000)
    private String decisionNote;
    @Size(max = 2000)
    private String requestedDeliveryAddress;
    private UUID replacementOrderId;
}
