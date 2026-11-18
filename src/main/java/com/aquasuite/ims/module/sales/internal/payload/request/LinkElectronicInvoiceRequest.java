package com.aquasuite.ims.module.sales.internal.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** Provider result that proves a sales invoice has been integrated as an electronic invoice. */
public record LinkElectronicInvoiceRequest(
        @NotBlank(message = "Provider code is required") String providerCode,
        @NotBlank(message = "Provider invoice ID is required") String providerInvoiceId,
        String invoiceSymbol,
        @NotNull(message = "Electronic signing time is required") OffsetDateTime signedAt) {
}
