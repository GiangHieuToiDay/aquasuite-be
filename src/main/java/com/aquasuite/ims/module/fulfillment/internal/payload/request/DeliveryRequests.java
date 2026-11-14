package com.aquasuite.ims.module.fulfillment.internal.payload.request;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class DeliveryRequests {
    private DeliveryRequests() {}
    public record Assign(@NotNull UUID shipperId,
                         @NotEmpty @Size(max = 100) List<@NotNull UUID> deliveryIds) {}
    public record Fail(@NotBlank @Size(max = 1000) String reason,
                       @NotNull @AssertTrue Boolean customerCancellationConfirmed) {}
    public record Reschedule(java.time.OffsetDateTime scheduledDate,
                             @NotBlank @Size(max = 1000) String reason) {}
}
