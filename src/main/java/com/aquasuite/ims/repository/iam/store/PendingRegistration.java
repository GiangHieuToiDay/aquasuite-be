package com.aquasuite.ims.repository.iam.store;

import java.util.UUID;

/**
 * Thông tin đăng ký chờ kích hoạt, chỉ tồn tại trong Redis tới khi xác minh email xong. Mật khẩu đã được
 * băm và {@code toString} che dữ liệu để không bao giờ lộ thông tin nhạy cảm vào log.
 */
public record PendingRegistration(
        UUID id, String fullName, String email, String phone, String passwordHash) {
    @Override
    public String toString() {
        return "PendingRegistration[redacted]";
    }
}
