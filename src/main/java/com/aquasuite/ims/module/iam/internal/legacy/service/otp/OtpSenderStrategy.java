package com.aquasuite.ims.module.iam.internal.legacy.service.otp;

import com.aquasuite.ims.module.iam.internal.legacy.entity.enums.RecoveryChannel;

public interface OtpSenderStrategy {
    RecoveryChannel getSupportedChannel();
    void sendRecoveryOtp(String destination, String otpCode);
}
