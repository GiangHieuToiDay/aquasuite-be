package com.aquasuite.ims.integration.iam;


import com.aquasuite.ims.enums.iam.RecoveryChannel;

public interface OtpSenderStrategy {
    RecoveryChannel getSupportedChannel();
    void sendRecoveryOtp(String destination, String otpCode);
}
