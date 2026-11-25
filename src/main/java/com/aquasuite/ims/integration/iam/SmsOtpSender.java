package com.aquasuite.ims.integration.iam;

import com.aquasuite.ims.enums.iam.RecoveryChannel;
import com.aquasuite.ims.exception.iam.IamPasswordErrorCode;
import com.aquasuite.ims.exception.AppException;
import org.springframework.stereotype.Component;

/**
 * Gửi mã OTP khôi phục mật khẩu qua SMS. Hiện chưa tích hợp nhà cung cấp SMS nên luôn báo kênh chưa
 * được hỗ trợ.
 */
@Component
public class SmsOtpSender implements OtpSenderStrategy {
    /** Chiến lược này phục vụ kênh SMS. */
    @Override
    public RecoveryChannel getSupportedChannel() {
        return RecoveryChannel.SMS;
    }

    /** Chưa hỗ trợ: ném lỗi SMS_CHANNEL_NOT_SUPPORTED_YET. */
    @Override
    public void sendRecoveryOtp(String phoneNumber, String otpCode) {
        // TODO: Tích hợp eSMS / Twilio / SpeedSMS ở giai đoạn sau
        throw new AppException(IamPasswordErrorCode.SMS_CHANNEL_NOT_SUPPORTED_YET);
    }
}
