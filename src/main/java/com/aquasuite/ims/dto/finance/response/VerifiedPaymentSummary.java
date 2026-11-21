package com.aquasuite.ims.dto.finance.response;

import java.math.BigDecimal;
import java.util.UUID;

public record VerifiedPaymentSummary(UUID orderId, BigDecimal verifiedAmount) {}
