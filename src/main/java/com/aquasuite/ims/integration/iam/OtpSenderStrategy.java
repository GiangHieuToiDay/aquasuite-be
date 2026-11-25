package com.aquasuite.ims.integration.iam;


import com.aquasuite.ims.enums.iam.RecoveryChannel;

/**
 * Chiến lược gửi mã OTP khôi phục mật khẩu theo từng kênh (email, SMS). {@code AuthServiceImpl} chọn
 * chiến lược theo kênh người dùng yêu cầu.
 */
public interface OtpSenderStrategy {
    /** Kênh mà chiến lược này hỗ trợ. */
    RecoveryChannel getSupportedChannel();
    /** Gửi mã OTP tới địa chỉ nhận (email hoặc số điện thoại). */
    void sendRecoveryOtp(String destination, String otpCode);
}
