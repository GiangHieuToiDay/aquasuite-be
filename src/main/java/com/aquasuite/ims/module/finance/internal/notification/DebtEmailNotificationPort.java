package com.aquasuite.ims.module.finance.internal.notification;

public interface DebtEmailNotificationPort {
    void send(String recipient, String subject, String body, byte[] attachment, String attachmentName);
}
