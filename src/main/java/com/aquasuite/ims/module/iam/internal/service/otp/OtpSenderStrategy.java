package com.aquasuite.ims.module.iam.internal.service.otp;

import com.aquasuite.ims.module.iam.internal.entity.enums.RecoveryChannel;

public interface OtpSenderStrategy {
    RecoveryChannel getSupportedChannel();
    void sendRecoveryOtp(String destination, String otpCode);
}
