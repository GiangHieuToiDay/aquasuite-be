package com.aquasuite.ims.dto.finance.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record DebtDashboardStatsResponse(
        BigDecimal totalOutstanding,
        long overdueCustomerCount,
        long creditBlockedCustomerCount) {}
