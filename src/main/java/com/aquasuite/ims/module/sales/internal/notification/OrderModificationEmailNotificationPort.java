package com.aquasuite.ims.module.sales.internal.notification;

public interface OrderModificationEmailNotificationPort {
    void send(String recipient, String subject, String body);
}
