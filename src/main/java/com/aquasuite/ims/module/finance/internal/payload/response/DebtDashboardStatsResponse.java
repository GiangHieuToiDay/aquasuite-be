package com.aquasuite.ims.module.finance.internal.payload.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record DebtDashboardStatsResponse(
        BigDecimal totalOutstanding,
        long overdueCustomerCount,
        long creditBlockedCustomerCount) {}
