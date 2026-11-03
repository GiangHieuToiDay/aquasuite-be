package com.aquasuite.ims.module.finance.api.dto;

import java.math.BigDecimal;

public record CreditAuthorizationDecision(boolean approved, String reasonCode, BigDecimal availableCredit) {}
