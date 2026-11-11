package com.aquasuite.ims.module.finance.internal.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** MVP payloads grouped together to keep the small COD API self-contained. */
public final class CodPayload {
    private CodPayload() {}
    public record Claim(@NotNull UUID deliveryId, @NotNull @Positive @Digits(integer=13, fraction=0) BigDecimal amount,
                        @NotNull OffsetDateTime collectedAt, @Size(max=1000) String note,
                        @NotNull UUID idempotencyKey) {}
    public record Review(@PositiveOrZero long expectedVersion, @Size(max=1000) String reason) {}
    public record Allocation(@NotNull UUID collectionId, @NotNull @Positive @Digits(integer=13, fraction=0) BigDecimal amount,
                             @PositiveOrZero long expectedVersion) {}
    public record Remit(@NotNull UUID idempotencyKey, @NotNull UUID shipperId,
                        @NotNull @Positive @Digits(integer=13, fraction=0) BigDecimal amount,
                        @NotEmpty @Size(max=50) List<@NotNull @Valid Allocation> items, @Size(max=1000) String note) {}
    public record Collection(UUID collectionId, UUID deliveryId, UUID orderId, UUID customerId, UUID shipperId,
                             String deliveryCode, BigDecimal expectedAmount, BigDecimal amount, String reviewStatus,
                             OffsetDateTime collectedAt, OffsetDateTime submittedAt, UUID reviewedBy,
                             OffsetDateTime reviewedAt, String rejectReason, String note, long version,
                             BigDecimal remittedAmount, BigDecimal heldAmount, String remittanceStatus) {}
    public record Receipt(UUID remittanceId, UUID shipperId, UUID receivedBy, OffsetDateTime receivedAt,
                          BigDecimal amount, String note, List<ReceiptItem> items) {}
    public record ReceiptItem(UUID collectionId, UUID orderId, UUID transactionId, BigDecimal amount) {}
    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
    public record Balance(UUID orderId, BigDecimal storeOutstanding, BigDecimal shipperHeld,
                          BigDecimal customerCollectible, BigDecimal pendingClaimAmount) {}
}
