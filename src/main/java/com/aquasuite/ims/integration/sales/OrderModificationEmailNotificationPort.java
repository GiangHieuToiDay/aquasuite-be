package com.aquasuite.ims.integration.sales;

public interface OrderModificationEmailNotificationPort {
    void send(String recipient, String subject, String body);
}
