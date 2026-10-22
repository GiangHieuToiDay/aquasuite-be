package com.aquasuite.ims.module.iam.internal.service.otp;


import com.aquasuite.ims.module.iam.internal.payload.request.RecoveryChannel;

public interface OtpSenderStrategy {
    RecoveryChannel getSupportedChannel();
    void sendRecoveryOtp(String destination, String otpCode);
}
