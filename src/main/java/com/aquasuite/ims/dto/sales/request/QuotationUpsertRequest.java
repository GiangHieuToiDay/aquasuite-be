package com.aquasuite.ims.dto.sales.request;

import com.aquasuite.ims.validation.sales.AllowedVatRate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotationUpsertRequest {

    @NotNull(message = "customerId is required")
    private UUID customerId;

    @NotBlank(message = "projectName is required")
    @Size(max = 255, message = "projectName max length is 255")
    private String projectName;

    @Size(max = 500, message = "deliveryAddress max length is 500")
    private String deliveryAddress;

    @Size(max = 255, message = "deliverySchedule max length is 255")
    private String deliverySchedule;

    @Email(message = "sentToEmail must be a valid email")
    @Size(max = 320, message = "sentToEmail max length is 320")
    private String sentToEmail;

    private LocalDate validUntil;

    @AllowedVatRate
    private BigDecimal vatRate;

    @PositiveOrZero(message = "shippingFee must be >= 0")
    @Digits(integer = 13, fraction = 2, message = "shippingFee max 13 integer digits and 2 decimals")
    private BigDecimal shippingFee;

    @Size(max = 2000, message = "note max length is 2000")
    private String note;

    @DecimalMin(value = "0", message = "depositPercent must be >= 0")
    @DecimalMax(value = "100", message = "depositPercent must be <= 100")
    @Digits(integer = 3, fraction = 2, message = "depositPercent max 3 integer digits and 2 decimals")
    private BigDecimal depositPercent;

    @DecimalMin(value = "0", message = "depositAmount must be >= 0")
    @Digits(integer = 13, fraction = 2, message = "depositAmount max 13 integer digits and 2 decimals")
    private BigDecimal depositAmount;

    @Min(value = 0, message = "creditTermDays must be >= 0")
    @Max(value = 365, message = "creditTermDays must be <= 365")
    private Integer creditTermDays;

    @DecimalMin(value = "0", message = "remainingDebtAmount must be >= 0")
    @Digits(integer = 13, fraction = 2, message = "remainingDebtAmount max 13 integer digits and 2 decimals")
    private BigDecimal remainingDebtAmount;

    @Size(max = 500, message = "debtCondition max length is 500")
    private String debtCondition;

    @Size(max = 500, message = "items max size is 500")
    @Builder.Default
    private List<@NotNull(message = "item must not be null") @Valid QuotationItemRequest> items = new ArrayList<>();

    @Size(max = 50, message = "milestones max size is 50")
    @Builder.Default
    private List<@NotNull(message = "milestone must not be null") @Valid QuotationPaymentMilestoneRequest> milestones = new ArrayList<>();
}
