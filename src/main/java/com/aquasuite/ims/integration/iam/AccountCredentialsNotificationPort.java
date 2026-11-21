package com.aquasuite.ims.integration.iam;

import java.util.UUID;

public interface AccountCredentialsNotificationPort {
    void sendInitialCredentials(UUID userId, String email, String username, String temporaryPassword);
}
