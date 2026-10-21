package com.aquasuite.ims.module.iam.internal.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class LoggingAccountCredentialsNotificationPort implements AccountCredentialsNotificationPort {
    @Override
    public void sendInitialCredentials(UUID userId, String email, String username, String temporaryPassword) {
        // Deliberately do not log the temporary password or other credentials.
        log.info("Initial account-credential notification queued for userId={}, email={}, username={}", userId, email, username);
    }
}
