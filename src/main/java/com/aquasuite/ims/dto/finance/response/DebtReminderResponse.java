package com.aquasuite.ims.dto.finance.response;

import java.math.BigDecimal;

public record DebtReminderResponse(
        BigDecimal outstandingBalance,
        String emailResult,
        String smsResult,
        String message) {}
