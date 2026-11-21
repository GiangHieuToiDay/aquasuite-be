package com.aquasuite.ims.integration.iam;

import com.aquasuite.ims.enums.iam.RecoveryChannel;
import com.aquasuite.ims.exception.iam.IamPasswordErrorCode;
import com.aquasuite.ims.exception.AppException;
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
