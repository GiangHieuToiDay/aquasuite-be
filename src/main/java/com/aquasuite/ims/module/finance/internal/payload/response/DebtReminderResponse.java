package com.aquasuite.ims.module.finance.internal.payload.response;

import java.math.BigDecimal;

public record DebtReminderResponse(
        BigDecimal outstandingBalance,
        String emailResult,
        String smsResult,
        String message) {}
