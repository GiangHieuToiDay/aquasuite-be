package com.aquasuite.ims.integration.finance;

public interface DebtEmailNotificationPort {
    void send(String recipient, String subject, String body, byte[] attachment, String attachmentName);
}
