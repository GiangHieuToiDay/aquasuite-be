package com.aquasuite.ims.module.finance.internal.payload;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** COD declarations are ordinary RECEIPT/COD transactions. */
public final class CodPayload {
    private CodPayload() {}
    public record Claim(@NotNull UUID deliveryId, @NotNull @Positive @Digits(integer=13, fraction=0) BigDecimal amount,
                        @NotNull OffsetDateTime collectedAt, @Size(max=1000) String note,
                        @NotNull UUID idempotencyKey) {}
    public record Review(@PositiveOrZero long expectedVersion, @Size(max=1000) String reason) {}
    public record Collection(UUID collectionId, UUID deliveryId, UUID orderId, UUID customerId, UUID shipperId,
                             BigDecimal amount, String status, OffsetDateTime collectedAt, OffsetDateTime submittedAt,
                             UUID reviewedBy, OffsetDateTime reviewedAt, String rejectReason, String note, long version) {}
    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
    public record Balance(UUID orderId, BigDecimal storeOutstanding, BigDecimal shipperHeld,
                          BigDecimal customerCollectible, BigDecimal pendingClaimAmount) {}
}
