package com.aquasuite.ims.module.sales.internal.payload.request;

import com.aquasuite.ims.module.finance.api.ProjectDepositPaymentApi.Method;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProjectDepositRequests {
    private ProjectDepositRequests() {}

    public record Deposit(@NotNull UUID requestId,
                          @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
                          @NotNull Method method,
                          @Size(max = 255) String referenceNo,
                          @Size(max = 1000) @Pattern(regexp = "https://[^\\s]+") String proofUrl,
                          @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,50}") String warehouseCode) {}

    public record Hold(@NotNull UUID requestId,
                       @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,50}") String warehouseCode) {}

    public record RequestedLine(@NotNull UUID orderItemId,
                                @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal quantity) {}

    public record Selection(@NotNull UUID requestId,
                            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,50}") String warehouseCode,
                            @NotEmpty @Size(max = 200) List<@NotNull @Valid RequestedLine> lines) {}

    public record NewOrderLine(@NotNull UUID variantId,
                               @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal quantity) {}

    public record CreateOrderDeposit(@NotNull UUID requestId, @NotNull UUID customerId,
                                     @NotEmpty @Size(max = 200) List<@NotNull @Valid NewOrderLine> lines,
                                     @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
                                     @NotNull Method method, @Size(max = 255) String referenceNo,
                                     @Size(max = 1000) @Pattern(regexp = "https://[^\\s]+") String proofUrl,
                                     @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,50}") String warehouseCode) {}

    public record DeliveryDate(@NotNull LocalDate date, @Size(max = 500) String note) {}

    public record Schedule(@Min(0) long expectedVersion,
                           @NotNull @Size(max = 24) List<@NotNull @Valid DeliveryDate> deliveries) {}
}
