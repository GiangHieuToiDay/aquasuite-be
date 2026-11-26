package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** Provider result that proves a sales invoice has been integrated as an electronic invoice. */
public record LinkElectronicInvoiceRequest(
        @NotBlank(message = "Provider code is required") @Size(max = 50) String providerCode,
        @NotBlank(message = "Provider invoice ID is required") @Size(max = 100) String providerInvoiceId,
        @Size(max = 20) String invoiceSymbol,
        @NotNull(message = "Electronic signing time is required")
        @PastOrPresent(message = "Electronic signing time must not be in the future")
        OffsetDateTime signedAt) {
}
