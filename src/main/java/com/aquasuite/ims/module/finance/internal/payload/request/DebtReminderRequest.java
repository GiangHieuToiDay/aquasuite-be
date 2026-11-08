package com.aquasuite.ims.module.finance.internal.payload.request;

import jakarta.validation.constraints.NotNull;

public record DebtReminderRequest(@NotNull ReminderType reminderType) {
    public enum ReminderType { UPCOMING, OVERDUE, FINAL }
}
