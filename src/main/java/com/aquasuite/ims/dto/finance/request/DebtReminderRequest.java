package com.aquasuite.ims.dto.finance.request;

import jakarta.validation.constraints.NotNull;

public record DebtReminderRequest(@NotNull ReminderType reminderType) {
    public enum ReminderType { UPCOMING, OVERDUE, FINAL }
}
