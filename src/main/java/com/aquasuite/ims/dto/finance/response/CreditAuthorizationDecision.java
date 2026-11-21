package com.aquasuite.ims.dto.finance.response;

import java.math.BigDecimal;

public record CreditAuthorizationDecision(boolean approved, String reasonCode, BigDecimal availableCredit) {}
