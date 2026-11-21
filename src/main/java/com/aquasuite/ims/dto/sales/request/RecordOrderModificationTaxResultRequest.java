package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class RecordOrderModificationTaxResultRequest {
    @NotBlank @Size(max = 100)
    private String taxResultReference;
    @NotNull
    private UUID resultingInvoiceId;
    private UUID replacementOrderId;
}
