package com.aquasuite.ims.module.iam.internal.notification;

import java.util.UUID;

public interface AccountCredentialsNotificationPort {
    void sendInitialCredentials(UUID userId, String email, String username, String temporaryPassword);
}
