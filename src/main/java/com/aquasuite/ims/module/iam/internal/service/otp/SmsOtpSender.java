package com.aquasuite.ims.module.iam.internal.service.otp;

import com.aquasuite.ims.module.iam.internal.payload.request.RecoveryChannel;
import com.aquasuite.ims.module.iam.internal.error.IamPasswordErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import org.springframework.stereotype.Component;

@Component
public class SmsOtpSender implements OtpSenderStrategy {
    @Override
    public RecoveryChannel getSupportedChannel() {
        return RecoveryChannel.SMS;
    }

    @Override
    public void sendRecoveryOtp(String phoneNumber, String otpCode) {
        // TODO: Tích hợp eSMS / Twilio / SpeedSMS ở giai đoạn sau
        throw new AppException(IamPasswordErrorCode.SMS_CHANNEL_NOT_SUPPORTED_YET);
    }
}
