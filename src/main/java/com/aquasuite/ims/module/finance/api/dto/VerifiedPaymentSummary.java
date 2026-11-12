package com.aquasuite.ims.module.finance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VerifiedPaymentSummary(UUID orderId, BigDecimal verifiedAmount) {}
